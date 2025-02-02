/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 01.11.2003 by RST.

// $Id: GameUtil.java,v 1.15 2005-12-27 21:02:31 salomo Exp $

package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class GameUtil {

    /**
     * Use the targets. (target and killtarget)
     * 
     * The global "activator" should be set to the entity that initiated the
     * firing.
     * 
     * If self.delay is set, a DelayedUse entity will be created that will
     * actually do the SUB_UseTargets after that many seconds have passed.
     * 
     * Centerprints any self.message to the activator.
     * 
     * Search for (string)targetname in all entities that match
     * (string)self.target and call their .use function
     */
    public static void G_UseTargets(SubgameEntity ent, SubgameEntity activator, GameExportsImpl gameExports) {

        if (ent.classname == null) {
            gameExports.gameImports.dprintf("edict with classname = null: " + ent.index);
        }

        // check for a delay
        SubgameEntity t;
        if (ent.delay != 0) {
            // create a temp object to fire at a later time
            t = gameExports.G_Spawn();
            t.classname = "DelayedUse";
            t.nextthink = gameExports.level.time + ent.delay;
            t.think = Think_Delay;
            t.activator = activator;
            if (activator == null)
                gameExports.gameImports.dprintf("Think_Delay with no activator\n");
            t.message = ent.message;
            t.target = ent.target;
            t.killtarget = ent.killtarget;
            return;
        }


        // print the message
        if ((ent.message != null)
                && (activator.svflags & Defines.SVF_MONSTER) == 0) {
            gameExports.gameImports.centerprintf(activator, "" + ent.message);
            if (ent.noise_index != 0)
                gameExports.gameImports.sound(activator, Defines.CHAN_AUTO,
                        ent.noise_index, 1, Defines.ATTN_NORM, 0);
            else
                gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                        .soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
        }

        // kill killtargets
        EdictIterator edit = null;

        if (ent.killtarget != null) {
            while ((edit = GameBase.G_Find(edit, GameBase.findByTargetName, ent.killtarget, gameExports)) != null) {
                t = edit.o;
                gameExports.freeEntity(t);
                if (!ent.inuse) {
                    gameExports.gameImports.dprintf("entity was removed while using killtargets\n");
                    return;
                }
            }
        }

        // fire (use) targets
        if (ent.target != null) {
            edit = null;
            while ((edit = GameBase.G_Find(edit, GameBase.findByTargetName, ent.target, gameExports)) != null) {
                t = edit.o;
                // doors fire area portals in a specific way
                if (Lib.Q_stricmp("func_areaportal", t.classname) == 0
                        && (Lib.Q_stricmp("func_door", ent.classname) == 0 || Lib
                                .Q_stricmp("func_door_rotating", ent.classname) == 0))
                    continue;

                if (t == ent) {
                    gameExports.gameImports.dprintf("WARNING: Entity tried to use itself.\n");
                } else {
                    if (t.use != null)
                        t.use.use(t, ent, activator, gameExports);
                }
                if (!ent.inuse) {
                    gameExports.gameImports
                            .dprintf("entity was removed while using targets\n");
                    return;
                }
            }
        }
    }

    /**
     * Kills all entities that would touch the proposed new positioning of ent.
     * Ent should be unlinked before calling this!
     */
    static boolean KillBox(SubgameEntity ent, GameExportsImpl gameExports) {

        while (true) {
            trace_t tr = gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs,
                    ent.s.origin, null, Defines.MASK_PLAYERSOLID);
            SubgameEntity target = (SubgameEntity) tr.ent;
            if (target == null || target == gameExports.g_edicts[0])
                break;

            // nail it
            GameCombat.T_Damage(target, ent, ent, Globals.vec3_origin, ent.s.origin,
                    Globals.vec3_origin, 100000, 0,
                    Defines.DAMAGE_NO_PROTECTION, GameDefines.MOD_TELEFRAG, gameExports);

            // if we didn't kill it, fail
            if (target.solid != 0)
                return false;
        }

        return true; // all clear
    }

    /** 
     * Returns true, if two edicts are on the same team. 
     */
    static boolean OnSameTeam(SubgameEntity ent1, SubgameEntity ent2, float dmflags) {
        if (0 == ((int) dmflags & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
            return false;

        if (ClientTeam(ent1, dmflags).equals(ClientTeam(ent2, dmflags)))
            return true;
        return false;
    }

    /** 
     * Returns the team string of an entity 
     * with respect to rteam_by_model and team_by_skin. 
     */
    private static String ClientTeam(SubgameEntity ent, float dmflags) {
        String value;

        gclient_t client = ent.getClient();
        if (client == null)
            return "";

        value = Info.Info_ValueForKey(client.pers.userinfo, "skin");

        int p = value.indexOf("/");

        if (p == -1)
            return value;

        if (((int) dmflags & Defines.DF_MODELTEAMS) != 0) {
            return value.substring(0, p);
        }

        return value.substring(p + 1, value.length());
    }

    static void ValidateSelectedItem(SubgameEntity ent, GameExportsImpl gameExports) {

        gclient_t cl = ent.getClient();

        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid

        GameItems.SelectNextItem(ent, -1, gameExports);
    }

    /**
     * Returns the range catagorization of an entity reletive to self 0 melee
     * range, will become hostile even if back is turned 1 visibility and
     * infront, or visibility and show hostile 2 infront and show hostile 3 only
     * triggered by damage.
     */
    public static int range(SubgameEntity self, edict_t other) {
        float[] v = { 0, 0, 0 };
        float len;

        Math3D.VectorSubtract(self.s.origin, other.s.origin, v);
        len = Math3D.VectorLength(v);
        if (len < GameDefines.MELEE_DISTANCE)
            return GameDefines.RANGE_MELEE;
        if (len < GameDefines.NEAR_DISTANCE)
            return GameDefines.RANGE_NEAR;
        if (len < GameDefines.MID_DISTANCE)
            return GameDefines.RANGE_MID;
        return GameDefines.RANGE_FAR;
    }

    static void AttackFinished(SubgameEntity self, float attack_finished) {
        self.monsterinfo.attack_finished = attack_finished;
    }

    /**
     * Returns true if the entity is in front (in sight) of self
     */
    public static boolean infront(SubgameEntity self, edict_t other) {
        float[] vec = { 0, 0, 0 };
        float dot;
        float[] forward = { 0, 0, 0 };

        Math3D.AngleVectors(self.s.angles, forward, null, null);
        Math3D.VectorSubtract(other.s.origin, self.s.origin, vec);
        Math3D.VectorNormalize(vec);
        dot = Math3D.DotProduct(vec, forward);

        if (dot > 0.3)
            return true;
        return false;
    }

    /**
     * Returns 1 if the entity is visible to self, even if not infront().
     */
    public static boolean visible(SubgameEntity self, SubgameEntity other, GameExportsImpl gameExports) {
        if (other == null)
            return false;

        float[] spot1 = { 0, 0, 0 };
        float[] spot2 = { 0, 0, 0 };
        trace_t trace;

        Math3D.VectorCopy(self.s.origin, spot1);
        spot1[2] += self.viewheight;
        Math3D.VectorCopy(other.s.origin, spot2);
        spot2[2] += other.viewheight;
        trace = gameExports.gameImports.trace(spot1, Globals.vec3_origin,
                Globals.vec3_origin, spot2, self, Defines.MASK_OPAQUE);

        if (trace.fraction == 1.0)
            return true;
        return false;
    }

    /**
     * Finds a target.
     * 
     * Self is currently not attacking anything, so try to find a target
     * 
     * Returns TRUE if an enemy was sighted
     * 
     * When a player fires a missile, the point of impact becomes a fakeplayer
     * so that monsters that see the impact will respond as if they had seen the
     * player.
     * 
     * To avoid spending too much time, only a single client (or fakeclient) is
     * checked each frame. This means multi player games will have slightly
     * slower noticing monsters.
     */
    static boolean FindTarget(SubgameEntity self, GameExportsImpl gameExports) {
        boolean heardit;
        int r;

        if ((self.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY) != 0) {
            if (self.goalentity != null && self.goalentity.inuse
                    && self.goalentity.classname != null) {
                if (self.goalentity.classname.equals("target_actor"))
                    return false;
            }
            
            //FIXME look for monsters?
            return false;
        }

        // if we're going to a combat point, just proceed
        if ((self.monsterinfo.aiflags & GameDefines.AI_COMBAT_POINT) != 0)
            return false;

        // if the first spawnflag bit is set, the monster will only wake up on
        // really seeing the player, not another monster getting angry or
        // hearing something
        // revised behavior so they will wake up if they "see" a player make a
        // noise but not weapon impact/explosion noises

        heardit = false;
        SubgameEntity client;
        if ((gameExports.level.sight_entity_framenum >= (gameExports.level.framenum - 1))
                && 0 == (self.spawnflags & 1)) {
            client = gameExports.level.sight_entity;
            if (client.enemy == self.enemy)             
                return false;            
        } else if (gameExports.level.sound_entity_framenum >= (gameExports.level.framenum - 1)) {
            client = gameExports.level.sound_entity;
            heardit = true;
        } else if (null != (self.enemy)
                && (gameExports.level.sound2_entity_framenum >= (gameExports.level.framenum - 1))
                && 0 != (self.spawnflags & 1)) {
            client = gameExports.level.sound2_entity;
            heardit = true;
        } else {
            client = gameExports.level.sight_client;
            if (client == null)
                return false; // no clients to get mad at
        }

        // if the entity went away, forget it
        if (!client.inuse)
            return false;

        if (client.getClient() != null) {
            if ((client.flags & GameDefines.FL_NOTARGET) != 0)
                return false;
        } else if ((client.svflags & Defines.SVF_MONSTER) != 0) {
            if (client.enemy == null)
                return false;
            if ((client.enemy.flags & GameDefines.FL_NOTARGET) != 0)
                return false;
        } else if (heardit) {
            if ((client.getOwner().flags & GameDefines.FL_NOTARGET) != 0)
                return false;
        } else
            return false;

        if (!heardit) {
            r = range(self, client);

            if (r == GameDefines.RANGE_FAR)
                return false;

            // this is where we would check invisibility
            // is client in an spot too dark to be seen?
            
            if (client.light_level <= 5)
                return false;

            if (!visible(self, client, gameExports))
                return false;
           

            if (r == GameDefines.RANGE_NEAR) {
                if (client.show_hostile < gameExports.level.time
                        && !infront(self, client))               
                    return false;                
            } else if (r == GameDefines.RANGE_MID) {
                if (!infront(self, client)) 
                    return false;               
            }

            if (client == self.enemy)
                return true; // JDC false;
            
            self.enemy = client;

            if (!self.enemy.classname.equals("player_noise")) {
                self.monsterinfo.aiflags &= ~GameDefines.AI_SOUND_TARGET;

                if (self.enemy.getClient() == null) {
                    self.enemy = self.enemy.enemy;
                    if (self.enemy.getClient() == null) {
                        self.enemy = null;
                        return false;
                    }
                }
            }
        } else {
            // heard it
            float[] temp = { 0, 0, 0 };

            if ((self.spawnflags & 1) != 0) {
                if (!visible(self, client, gameExports))
                    return false;
            } else {
                if (!gameExports.gameImports.inPHS(self.s.origin, client.s.origin))
                    return false;
            }

            Math3D.VectorSubtract(client.s.origin, self.s.origin, temp);

            if (Math3D.VectorLength(temp) > 1000) // too far to hear
                return false;


            // check area portals - if they are different and not connected then
            // we can't hear it
            if (client.areanum != self.areanum)
                if (!gameExports.gameImports.AreasConnected(self.areanum, client.areanum))
                    return false;

            self.ideal_yaw = Math3D.vectoyaw(temp);
            M.rotateToIdealYaw(self);

            // hunt the sound for a bit; hopefully find the real player
            self.monsterinfo.aiflags |= GameDefines.AI_SOUND_TARGET;
            
            if (client == self.enemy)
                return true; // JDC false;
             
            self.enemy = client;             
        }
        
        // got one
        FoundTarget(self, gameExports);

        if (0 == (self.monsterinfo.aiflags & GameDefines.AI_SOUND_TARGET)
                && (self.monsterinfo.sight != null))
            self.monsterinfo.sight.interact(self, self.enemy, gameExports);

        return true;
    }

    public static void FoundTarget(SubgameEntity self, GameExportsImpl gameExports) {
        // let other monsters see this monster for a while
        if (self.enemy.getClient() != null) {
            gameExports.level.sight_entity = self;
            gameExports.level.sight_entity_framenum = gameExports.level.framenum;
            gameExports.level.sight_entity.light_level = 128;
        }

        self.show_hostile = (int) gameExports.level.time + 1; // wake up other
                                                           // monsters

        Math3D.VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
        self.monsterinfo.trail_time = gameExports.level.time;

        if (self.combattarget == null) {
            GameAI.HuntTarget(self, gameExports);
            return;
        }

        self.goalentity = self.movetarget = GameBase
                .G_PickTarget(self.combattarget, gameExports);
        if (self.movetarget == null) {
            self.goalentity = self.movetarget = self.enemy;
            GameAI.HuntTarget(self, gameExports);
            gameExports.gameImports.dprintf("" + self.classname + "at "
                    + Lib.vtos(self.s.origin) + ", combattarget "
                    + self.combattarget + " not found\n");
            return;
        }

        // clear out our combattarget, these are a one shot deal
        self.combattarget = null;
        self.monsterinfo.aiflags |= GameDefines.AI_COMBAT_POINT;

        // clear the targetname, that point is ours!
        self.movetarget.targetname = null;
        self.monsterinfo.pausetime = 0;

        // run for it
        self.monsterinfo.run.think(self, gameExports);
    }

    private static EntThinkAdapter Think_Delay = new EntThinkAdapter() {
    	public String getID() { return "Think_Delay"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            G_UseTargets(ent, ent.activator, gameExports);
            gameExports.freeEntity(ent);
            return true;
        }
    };

    static EntThinkAdapter G_FreeEdictA = new EntThinkAdapter() {
    	public String getID() { return "G_FreeEdictA"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            gameExports.freeEntity(ent);
            return false;
        }
    };

    public static EntThinkAdapter M_CheckAttack = new EntThinkAdapter() {
    	public String getID() { return "M_CheckAttack"; }

        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            float[] spot1 = { 0, 0, 0 };

            float[] spot2 = { 0, 0, 0 };
            float chance;
            trace_t tr;

            if (self.enemy.health > 0) {
                // see if any entities are in the way of the shot
                Math3D.VectorCopy(self.s.origin, spot1);
                spot1[2] += self.viewheight;
                Math3D.VectorCopy(self.enemy.s.origin, spot2);
                spot2[2] += self.enemy.viewheight;

                tr = gameExports.gameImports.trace(spot1, null, null, spot2, self,
                        Defines.CONTENTS_SOLID | Defines.CONTENTS_MONSTER
                                | Defines.CONTENTS_SLIME
                                | Defines.CONTENTS_LAVA
                                | Defines.CONTENTS_WINDOW);

                // do we have a clear shot?
                if (tr.ent != self.enemy)
                    return false;
            }

            // melee attack
            if (gameExports.enemy_range == GameDefines.RANGE_MELEE) {
                // don't always melee in easy mode
                if (gameExports.gameCvars.skill.value == 0 && (Lib.rand() & 3) != 0)
                    return false;
                if (self.monsterinfo.melee != null)
                    self.monsterinfo.attack_state = GameDefines.AS_MELEE;
                else
                    self.monsterinfo.attack_state = GameDefines.AS_MISSILE;
                return true;
            }

            // missile attack
            if (self.monsterinfo.attack == null)
                return false;

            if (gameExports.level.time < self.monsterinfo.attack_finished)
                return false;

            if (gameExports.enemy_range == GameDefines.RANGE_FAR)
                return false;

            if ((self.monsterinfo.aiflags & GameDefines.AI_STAND_GROUND) != 0) {
                chance = 0.4f;
            } else if (gameExports.enemy_range == GameDefines.RANGE_MELEE) {
                chance = 0.2f;
            } else if (gameExports.enemy_range == GameDefines.RANGE_NEAR) {
                chance = 0.1f;
            } else if (gameExports.enemy_range == GameDefines.RANGE_MID) {
                chance = 0.02f;
            } else {
                // chance = 0f
                return false;
            }

            if (gameExports.gameCvars.skill.value == 0)
                chance *= 0.5;
            else if (gameExports.gameCvars.skill.value >= 2)
                chance *= 2;

            if (Lib.random() < chance) {
                self.monsterinfo.attack_state = GameDefines.AS_MISSILE;
                AttackFinished(self, gameExports.level.time + 2
                        * Lib.random());
                return true;
            }

            if ((self.flags & GameDefines.FL_FLY) != 0) {
                if (Lib.random() < 0.3f)
                    self.monsterinfo.attack_state = GameDefines.AS_SLIDING;
                else
                    self.monsterinfo.attack_state = GameDefines.AS_STRAIGHT;
            }

            return false;

        }
    };

    static EntUseAdapter monster_use = new EntUseAdapter() {
    	public String getID() { return "monster_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (self.enemy != null)
                return;
            if (self.health <= 0)
                return;
            if ((activator.flags & GameDefines.FL_NOTARGET) != 0)
                return;
            if ((null == activator.getClient())
                    && 0 == (activator.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY))
                return;

            // delay reaction so if the monster is teleported, its sound is
            // still heard
            self.enemy = activator;
            FoundTarget(self, gameExports);
        }
    };
}
