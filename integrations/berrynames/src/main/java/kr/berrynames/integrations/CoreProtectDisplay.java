package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Formats visible log fields only. Click commands, hover payloads and logged message bodies stay intact. */
final class CoreProtectDisplay {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern ROW = Pattern.compile("^<COMPONENT>POPUP\\|(?:§[0-9a-fk-or])*[0-9]{4}-[0-9]{2}-[0-9]{2} [^|]*\\|[^|]*</COMPONENT>", Pattern.CASE_INSENSITIVE);
    private final Supplier<BerryNamesApi> names;
    private final Map<String,String> translations;
    private final TextColor accent;
    private final boolean displayNames;

    CoreProtectDisplay(Supplier<BerryNamesApi> names, Map<String,String> translations, TextColor accent, boolean displayNames) {
        this.names=names; this.translations=Map.copyOf(translations); this.accent=accent; this.displayNames=displayNames;
    }

    static boolean isRow(String raw) { return raw != null && ROW.matcher(raw).find(); }
    static boolean hasMaterial(String raw, String bypass) {
        // Chat/sign bodies and historical username records use a plain white dash.
        return bypass == null && (raw.contains("§a+ ") || raw.contains("§c- "));
    }

    Component decorate(Component original, boolean materialFields) {
        BerryNamesApi api=names.get();
        var identities=api==null ? List.<BerryNamesApi.Identity>of() : api.identities();
        return visit(normalize(original), null, new Cursor(), identities, materialFields);
    }
    private Component normalize(Component original) {
        var children=original.children().stream().map(this::normalize).toList();
        if (original instanceof TextComponent text && text.content().indexOf('§')>=0) {
            // CoreProtect embeds legacy codes in Bungee text nodes, including console output.
            return Component.empty().style(original.style()).append(LEGACY.deserialize(text.content())).append(children);
        }
        return original.children(children);
    }
    private Component visit(Component node, TextColor parentColor, Cursor cursor, List<BerryNamesApi.Identity> identities, boolean materials) {
        TextColor color=node.color()==null ? parentColor : node.color();
        Component result=node;
        if (node instanceof TextComponent text && !text.content().isBlank() && (Objects.equals(color,accent)||Objects.equals(color,NamedTextColor.DARK_AQUA))) {
            if (!cursor.actorSeen) {
                cursor.actorSeen=true;
                if (displayNames) {
                    String account=text.content().replaceFirst(":\\s*$", "");
                    var matches=identities.stream().filter(i->i.accountName().equalsIgnoreCase(account)).toList();
                    if (matches.size()==1) {
                        var identity=matches.getFirst();
                        Component details=Component.text("계정명: "+identity.accountName()+"\nUUID: "+identity.uuid()+"\n베리 번호: #"+identity.number());
                        if (node.hoverEvent()!=null && node.hoverEvent().action()==HoverEvent.Action.SHOW_TEXT)
                            details=details.appendNewline().append((Component)node.hoverEvent().value());
                        var renamed=text.content(identity.displayName()+text.content().substring(account.length()));
                        if (node.hoverEvent()==null||node.hoverEvent().action()==HoverEvent.Action.SHOW_TEXT) renamed=renamed.hoverEvent(details);
                        result=renamed;
                    }
                }
            } else if (materials) {
                String key=translations.get(text.content().toLowerCase(Locale.ROOT));
                if (key!=null) result=Component.translatable(key).fallback(text.content()).style(node.style());
            }
        }
        var children=new ArrayList<Component>();
        for (Component child:node.children()) children.add(visit(child,color,cursor,identities,materials));
        return result.children(children);
    }
    private static final class Cursor { boolean actorSeen; }
}
