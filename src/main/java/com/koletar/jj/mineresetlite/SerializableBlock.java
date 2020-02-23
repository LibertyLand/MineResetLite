package com.koletar.jj.mineresetlite;

import org.bukkit.Material;

/**
 * @author jjkoletar
 * @author Artuto
 */
public class SerializableBlock
{
    private Material type;

    public SerializableBlock(Material material)
    {
        this(material.toString());
    }

    public SerializableBlock(String self)
    {
        this.type = Material.getMaterial(self);

        if(type == null)
            throw new IllegalArgumentException("Invalid Material type: " + self);
    }

    public Material getType()
    {
        return type;
    }

    @Override
    public String toString()
    {
        return type.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof SerializableBlock && getType().equals(((SerializableBlock) o).getType());
    }
}
