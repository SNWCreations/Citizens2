package net.citizensnpcs.npc;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Iterator;
import java.util.Map;

import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCCreateEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCDataStore;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.util.NMS;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class CitizensNPCRegistry implements NPCRegistry {
    private final NPCCollection npcs = TROVE_EXISTS ? new TroveNPCCollection() : new MapNPCCollection();
    private final NPCDataStore saves;

    public CitizensNPCRegistry(NPCDataStore store) {
        saves = store;
    }

    @Override
    public NPC createNPC(EntityType type, int id, String name) {
        Preconditions.checkNotNull(name, "name cannot be null");
        Preconditions.checkNotNull(type, "type cannot be null");
        CitizensNPC npc = getByType(type, id, name);
        if (npc == null)
            throw new IllegalStateException("Could not create NPC.");
        npcs.put(npc.getId(), npc);
        Bukkit.getPluginManager().callEvent(new NPCCreateEvent(npc));
        return npc;
    }

    @Override
    public NPC createNPC(EntityType type, String name) {
        return createNPC(type, generateUniqueId(), name);
    }

    @Override
    public void deregister(NPC npc) {
        npcs.remove(npc.getId());
        if (saves != null) {
            saves.clearData(npc);
        }
        npc.despawn(DespawnReason.REMOVAL);
    }

    @Override
    public void deregisterAll() {
        Iterator<NPC> itr = iterator();
        while (itr.hasNext()) {
            NPC npc = itr.next();
            itr.remove();
            npc.despawn(DespawnReason.REMOVAL);
            for (Trait t : npc.getTraits()) {
                t.onRemove();
            }
            if (saves != null) {
                saves.clearData(npc);
            }
        }
    }

    private int generateUniqueId() {
        return saves.createUniqueNPCId(this);
    }

    @Override
    public NPC getById(int id) {
        if (id < 0)
            throw new IllegalArgumentException("invalid id");
        return npcs.get(id);
    }

    private CitizensNPC getByType(EntityType type, int id, String name) {
        return new CitizensNPC(id, name, EntityControllers.createForType(type), this);
    }

    @Override
    public NPC getNPC(Entity entity) {
        if (entity == null)
            return null;
        if (entity instanceof NPCHolder)
            return ((NPCHolder) entity).getNPC();
        if (!(entity instanceof LivingEntity))
            return null;
        Object handle = NMS.getHandle((LivingEntity) entity);
        return handle instanceof NPCHolder ? ((NPCHolder) handle).getNPC() : null;
    }

    @Override
    public boolean isNPC(Entity entity) {
        return getNPC(entity) != null;
    }

    @Override
    public Iterator<NPC> iterator() {
        return npcs.iterator();
    }

    public static class MapNPCCollection implements NPCCollection {
        private final Map<Integer, NPC> npcs = Maps.newHashMap();

        @Override
        public NPC get(int id) {
            return npcs.get(id);
        }

        @Override
        public Iterator<NPC> iterator() {
            return npcs.values().iterator();
        }

        @Override
        public void put(int id, NPC npc) {
            npcs.put(id, npc);
        }

        @Override
        public void remove(int id) {
            npcs.remove(id);
        }
    }

    public static interface NPCCollection extends Iterable<NPC> {
        public NPC get(int id);

        public void put(int id, NPC npc);

        public void remove(int id);
    }

    public static class TroveNPCCollection implements NPCCollection {
        private final TIntObjectHashMap<NPC> npcs = new TIntObjectHashMap<NPC>();

        @Override
        public NPC get(int id) {
            return npcs.get(id);
        }

        @Override
        public Iterator<NPC> iterator() {
            return npcs.valueCollection().iterator();
        }

        @Override
        public void put(int id, NPC npc) {
            npcs.put(id, npc);
        }

        @Override
        public void remove(int id) {
            npcs.remove(id);
        }
    }

    private static boolean TROVE_EXISTS = false;
    static {
        // allow trove dependency to be optional for debugging purposes
        try {
            Class.forName("gnu.trove.map.hash.TIntObjectHashMap").newInstance();
            TROVE_EXISTS = true;
        } catch (Exception e) {
        }
    }
}