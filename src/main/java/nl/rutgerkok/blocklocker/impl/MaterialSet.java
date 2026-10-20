package nl.rutgerkok.blocklocker.impl;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.*;

final class MaterialSet {
    // Used to reconstruct the set for the config
    private final Set<Material> materials = new HashSet<>();
    private final Set<Tag<Material>> tags = new HashSet<>();

    // Used for fast lookups
    private final Set<Material> flattened = new HashSet<>();

    /**
     * Checks whether the set contains the given material. This includes materials in tags.
     * @param material The material.
     * @return True if the material is in the set, false otherwise.
     */
    boolean contains(Material material) {
        return flattened.contains(material);
    }

    /**
     * Adds a material to the set.
     * @param material The material.
     */
    void addMaterial(Material material) {
        // Add to both collections
        materials.add(material);
        flattened.add(material);
    }

    /**
     * Adds a tag to the set. All materials in the tag will be added to the flattened set.
     * @param tag The tag.
     */
    void addTag(Tag<Material> tag) {
        tags.add(tag);
        flattened.addAll(tag.getValues());
    }

    /**
     * Gets all materials in the set, including those in tags.
     * @return An unmodifiable collection of all materials in the set.
     * @see #toConfigStringList() For a list of strings that can be used in the config, keeping the tags.
     */
    Collection<Material> getAllFlattened() {
        return Collections.unmodifiableSet(flattened);
    }

    /**
     * Converts the set to a list of strings that can be used in the config. Tags are kept as tags, prefixed with a #.
     * @return A list of strings representing the materials and tags in the set.
     */
    List<String> toConfigStringList() {
        List<String> result = new ArrayList<>();
        for (Material material : materials) {
            result.add(material.getKey().toString());
        }
        for (Tag<Material> tag : tags) {
            result.add("#" + tag.getKey());
        }
        result.sort(null);
        return result;
    }
}
