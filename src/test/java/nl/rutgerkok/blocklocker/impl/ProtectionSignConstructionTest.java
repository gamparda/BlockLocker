package nl.rutgerkok.blocklocker.impl;

import nl.rutgerkok.blocklocker.SignType;
import nl.rutgerkok.blocklocker.group.CombinedGroupSystem;
import nl.rutgerkok.blocklocker.impl.profile.NullTranslator;
import nl.rutgerkok.blocklocker.impl.profile.ProfileFactoryImpl;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionSignConstructionTest {
    @Test void immutableProfileListsAreValidSignContents() {
        var factory = new ProfileFactoryImpl(new CombinedGroupSystem(),new NullTranslator());
        var owner=factory.fromNameAndUniqueId("Alpha",Optional.of(UUID.randomUUID()));
        var sign=new ProtectionSignImpl(new Location(null,0,0,0),SignType.PRIVATE,List.of(owner),true);
        assertEquals(owner,sign.getProfiles().getFirst());
        assertEquals(1,sign.withProfiles(List.of(owner)).getProfiles().size());
    }
}
