package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CoreProtectDisplayTest {
    final UUID uuid=UUID.randomUUID();
    final AtomicReference<String> current=new AtomicReference<>("감파르다");
    final TextColor blue=TextColor.color(0x31b0e8);
    final BerryNamesApi names=new BerryNamesApi(){
        public List<Identity> identities(){return List.of(new Identity(uuid,1,"gamparda",current.get(),"베리일"));}
        public Optional<Identity> find(UUID id){return identities().stream().filter(i->i.uuid().equals(id)).findFirst();}
        public List<Identity> findAliases(String query){return List.of();}
    };
    CoreProtectDisplay display(){return new CoreProtectDisplay(()->names,Map.of("grass_block","block.minecraft.grass_block"),blue,true);}
    String plain(Component component){return PlainTextComponentSerializer.plainText().serialize(component);}
    List<Component> all(Component root){var list=new ArrayList<Component>();list.add(root);root.children().forEach(child->list.addAll(all(child)));return list;}
    Component row(){return Component.empty().append(Component.text("0.00/m 전 + ",NamedTextColor.GRAY))
            .append(Component.text("gamparda",blue)).append(Component.text("이(가) ",NamedTextColor.WHITE))
            .append(Component.text("grass_block",blue)).append(Component.text("을(를) 설치함",NamedTextColor.WHITE));}
    @Test void replacesActorAndEmitsVanillaTranslationKey() {
        Component result=display().decorate(row(),true);
        assertTrue(plain(result).contains("감파르다")); assertFalse(plain(result).contains("gamparda"));
        var actor=all(result).stream().filter(n->n instanceof TextComponent t&&t.content().equals("감파르다")).findFirst().orElseThrow();
        assertEquals(blue,actor.color());
        String hover=plain((Component)actor.hoverEvent().value());assertTrue(hover.contains("gamparda"));assertTrue(hover.contains(uuid.toString()));
        assertTrue(all(result).stream().anyMatch(n->n instanceof TranslatableComponent t&&t.key().equals("block.minecraft.grass_block")));
    }
    @Test void understandsLegacyCodesInsideNativeBungeeNodes() {
        var root=new net.md_5.bungee.api.chat.TextComponent();
        root.addExtra("§70.00/m 전 §a+ ");
        var actor=new net.md_5.bungee.api.chat.TextComponent("§mgamparda§f이(가) ");actor.setColor(net.md_5.bungee.api.ChatColor.of("#31b0e8"));root.addExtra(actor);
        var material=new net.md_5.bungee.api.chat.TextComponent("§mgrass_block§f을(를) 설치함");material.setColor(net.md_5.bungee.api.ChatColor.of("#31b0e8"));root.addExtra(material);
        var original=GsonComponentSerializer.gson().deserialize(net.md_5.bungee.chat.ComponentSerializer.toString(root));
        var result=display().decorate(original,true);
        assertTrue(plain(result).contains("감파르다"));
        assertTrue(all(result).stream().anyMatch(n->n instanceof TranslatableComponent t&&t.key().equals("block.minecraft.grass_block")));
        assertTrue(all(result).stream().anyMatch(n->n instanceof TextComponent t&&t.content().equals("감파르다")&&t.decoration(TextDecoration.STRIKETHROUGH)==TextDecoration.State.TRUE));
    }
    @Test void consoleLegacyAndLiveRenamesWork() {
        var raw=Component.text("§70.00/m 전 §a+ §3gamparda§f이(가) §3grass_block§f을(를) 설치함");
        assertTrue(plain(display().decorate(raw,true)).contains("감파르다"));
        current.set("새대장");assertTrue(plain(display().decorate(raw,true)).contains("새대장"));
    }
    @Test void clickCommandsAndItemTooltipsAreUnchanged() {
        var material=Component.text("grass_block",blue).hoverEvent(Component.text("Original item NBT: gamparda grass_block"))
                .clickEvent(ClickEvent.runCommand("/co give #12"));
        var input=Component.empty().append(Component.text("gamparda",blue).clickEvent(ClickEvent.suggestCommand("/co l u:gamparda")))
                .append(material).append(Component.text("coordinates").clickEvent(ClickEvent.runCommand("/co teleport wid:1 1 2 3")));
        var result=display().decorate(input,true);
        assertEquals(input.children().get(0).clickEvent(),result.children().get(0).clickEvent());
        assertEquals(material.hoverEvent(),result.children().get(1).hoverEvent());assertEquals(material.clickEvent(),result.children().get(1).clickEvent());
        assertEquals(input.children().get(2),result.children().get(2));
    }
    @Test void preservesLoggedMessagesAndHistoricalNames() {
        Component input=Component.empty().append(Component.text("gamparda",blue)).append(Component.text(": ",NamedTextColor.WHITE))
                .append(Component.text("gamparda grass_block",blue));
        String rendered=plain(display().decorate(input,false));
        assertEquals("감파르다: gamparda grass_block",rendered);
        var history=Component.empty().append(Component.text("gamparda",blue)).append(Component.text(" -> ")).append(Component.text("grass_block",blue));
        assertTrue(all(display().decorate(history,false)).stream().noneMatch(n->n instanceof TranslatableComponent));
    }
    @Test void duplicateAccountsRemainUnmodified() {
        var duplicate=new BerryNamesApi(){
            public List<Identity> identities(){return List.of(names.identities().getFirst(),new Identity(UUID.randomUUID(),2,"GAMPARDA","다른사람","베리이"));}
            public Optional<Identity> find(UUID id){return Optional.empty();}public List<Identity> findAliases(String q){return List.of();}
        };
        var display=new CoreProtectDisplay(()->duplicate,Map.of("gamparda","fake.key"),blue,true);
        var result=display.decorate(Component.text("gamparda",blue),true);
        assertEquals("gamparda",plain(result));assertTrue(result instanceof TextComponent);
    }
    @Test void nonLogRowsBypassRenderingAndBodiesNeverEnableMaterialReplacement() {
        String time="<COMPONENT>POPUP|§72026-10-21 01:04:00 KST|§70.00/m 전</COMPONENT>";
        assertTrue(CoreProtectDisplay.isRow(time+" §a+ §3gamparda§f placed §3grass_block§f"));
        assertFalse(CoreProtectDisplay.isRow("§f----- §3CoreProtect§f -----"));
        assertTrue(CoreProtectDisplay.hasMaterial(time+" §a+ ",null));
        assertFalse(CoreProtectDisplay.hasMaterial(time+" §a+ ","gamparda grass_block"));
        assertFalse(CoreProtectDisplay.hasMaterial(time+" §f- §3gamparda -> grass_block",null));
    }
    @Test void missingNameServiceStillTranslatesMaterialAndUnknownFieldsStayReadable() {
        var display=new CoreProtectDisplay(()->null,Map.of("grass_block","block.minecraft.grass_block"),blue,true);
        var result=display.decorate(row(),true);assertTrue(plain(result).contains("gamparda"));
        assertTrue(all(result).stream().anyMatch(n->n instanceof TranslatableComponent));
        assertEquals("grass_block",plain(display.decorate(Component.text("grass_block",blue),true)));
    }
}
