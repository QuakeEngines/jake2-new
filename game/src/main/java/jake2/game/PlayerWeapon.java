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

// Created on 16.11.2003 by RST.

package jake2.game;

import jake2.game.adapters.EntInteractAdapter;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.ItemDropAdapter;
import jake2.game.adapters.ItemUseAdapter;
import jake2.game.items.GameItem;
import jake2.game.monsters.M_Player;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.WeaponSoundMessage;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class PlayerWeapon {

    public static EntThinkAdapter Weapon_Grenade = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Grenade"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            gclient_t client = ent.getClient();
            if ((client.newweapon != null)
                    && (client.weaponstate == WeaponStates.WEAPON_READY)) {
                ChangeWeapon(ent, gameExports);
                return true;
            }

            if (client.weaponstate == WeaponStates.WEAPON_ACTIVATING) {
                client.weaponstate = WeaponStates.WEAPON_READY;
                client.getPlayerState().gunframe = 16;
                return true;
            }

            if (client.weaponstate == WeaponStates.WEAPON_READY) {
                if (((client.latched_buttons | client.buttons) & Defines.BUTTON_ATTACK) != 0) {
                    client.latched_buttons &= ~Defines.BUTTON_ATTACK;
                    if (0 != client.pers.inventory[client.ammo_index]) {
                        client.getPlayerState().gunframe = 1;
                        client.weaponstate = WeaponStates.WEAPON_FIRING;
                        client.grenade_time = 0;
                    } else {
                        if (gameExports.level.time >= ent.pain_debounce_time) {
                            gameExports.gameImports.sound(ent, Defines.CHAN_VOICE,
                                    gameExports.gameImports
                                            .soundindex("weapons/noammo.wav"),
                                    1, Defines.ATTN_NORM, 0);
                            ent.pain_debounce_time = gameExports.level.time + 1;
                        }
                        NoAmmoWeaponChange(ent, gameExports);
                    }
                    return true;
                }

                if ((client.getPlayerState().gunframe == 29)
                        || (client.getPlayerState().gunframe == 34)
                        || (client.getPlayerState().gunframe == 39)
                        || (client.getPlayerState().gunframe == 48)) {
                    if ((Lib.rand() & 15) != 0)
                        return true;
                }

                if (++client.getPlayerState().gunframe > 48)
                    client.getPlayerState().gunframe = 16;
                return true;
            }

            if (client.weaponstate == WeaponStates.WEAPON_FIRING) {
                if (client.getPlayerState().gunframe == 5)
                    gameExports.gameImports.sound(ent, Defines.CHAN_WEAPON, gameExports.gameImports
                            .soundindex("weapons/hgrena1b.wav"), 1,
                            Defines.ATTN_NORM, 0);

                if (client.getPlayerState().gunframe == 11) {
                    if (0 == client.grenade_time) {
                        client.grenade_time = gameExports.level.time
                                + GameDefines.GRENADE_TIMER + 0.2f;
                        client.weapon_sound = gameExports.gameImports
                                .soundindex("weapons/hgrenc1b.wav");
                    }

                    // they waited too long, detonate it in their hand
                    if (!client.grenade_blew_up
                            && gameExports.level.time >= client.grenade_time) {
                        client.weapon_sound = 0;
                        weapon_grenade_fire(ent, true, gameExports);
                        client.grenade_blew_up = true;
                    }

                    if ((client.buttons & Defines.BUTTON_ATTACK) != 0)
                        return true;

                    if (client.grenade_blew_up) {
                        if (gameExports.level.time >= client.grenade_time) {
                            client.getPlayerState().gunframe = 15;
                            client.grenade_blew_up = false;
                        } else {
                            return true;
                        }
                    }
                }

                if (client.getPlayerState().gunframe == 12) {
                    client.weapon_sound = 0;
                    weapon_grenade_fire(ent, false, gameExports);
                }

                if ((client.getPlayerState().gunframe == 15)
                        && (gameExports.level.time < client.grenade_time))
                    return true;

                client.getPlayerState().gunframe++;

                if (client.getPlayerState().gunframe == 16) {
                    client.grenade_time = 0;
                    client.weaponstate = WeaponStates.WEAPON_READY;
                }
            }
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * GRENADE LAUNCHER
     * 
     * ======================================================================
     */

    public static EntThinkAdapter weapon_grenadelauncher_fire = new EntThinkAdapter() {
    	public String getID() { return "weapon_grenadelauncher_fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            float[] offset = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] start = { 0, 0, 0 };
            int damage = 120;
            float radius;

            radius = damage + 40;
            if (gameExports.is_quad)
                damage *= 4;

            Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
            gclient_t client = ent.getClient();
            Math3D.AngleVectors(client.v_angle, forward, right, null);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);

            Math3D.VectorScale(forward, -2, client.kick_origin);
            client.kick_angles[0] = -1;

            GameWeapon.fire_grenade(ent, start, forward, damage, 600, 2.5f, radius, gameExports);

            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_GRENADE | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            client.getPlayerState().gunframe++;

            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_GrenadeLauncher = new EntThinkAdapter() {
    	public String getID() { return "Weapon_GrenadeLauncher"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 34, 51, 59, 0 };
            int fire_frames[] = { 6, 0 };

            Weapon_Generic(ent, 5, 16, 59, 64, pause_frames, fire_frames,
                    weapon_grenadelauncher_fire, gameExports);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * ROCKET
     * 
     * ======================================================================
     */

    public static EntThinkAdapter Weapon_RocketLauncher_Fire = new EntThinkAdapter() {
    	public String getID() { return "Weapon_RocketLauncher_Fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            float[] offset = { 0, 0, 0 }, start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            int damage;
            float damage_radius;
            int radius_damage;

            damage = 100 + (int) (Lib.random() * 20.0);
            radius_damage = 120;
            damage_radius = 120;
            if (gameExports.is_quad) {
                damage *= 4;
                radius_damage *= 4;
            }

            gclient_t client = ent.getClient();
            Math3D.AngleVectors(client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, client.kick_origin);
            client.kick_angles[0] = -1;

            Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);
            GameWeapon.fire_rocket(ent, start, forward, damage, 650, damage_radius,
                    radius_damage, gameExports);

            // send muzzle flash
            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_ROCKET | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            client.getPlayerState().gunframe++;

            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_RocketLauncher = new EntThinkAdapter() {
    	public String getID() { return "Weapon_RocketLauncher"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 25, 33, 42, 50, 0 };
            int fire_frames[] = { 5, 0 };

            Weapon_Generic(ent, 4, 12, 50, 54, pause_frames, fire_frames,
                    Weapon_RocketLauncher_Fire, gameExports);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Blaster_Fire = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Blaster_Fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int damage;

            if (gameExports.gameCvars.deathmatch.value != 0)
                damage = 15;
            else
                damage = 10;
            Blaster_Fire(ent, Globals.vec3_origin, damage, false,
                    Defines.EF_BLASTER, gameExports);
            ent.getClient().getPlayerState().gunframe++;
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Blaster = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Blaster"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 19, 32, 0 };
            int fire_frames[] = { 5, 0 };

            Weapon_Generic(ent, 4, 8, 52, 55, pause_frames, fire_frames,
                    Weapon_Blaster_Fire, gameExports);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_HyperBlaster_Fire = new EntThinkAdapter() {
    	public String getID() { return "Weapon_HyperBlaster_Fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            float rotation;
            float[] offset = { 0, 0, 0 };
            int effect;
            int damage;

            gclient_t client = ent.getClient();
            client.weapon_sound = gameExports.gameImports
                    .soundindex("weapons/hyprbl1a.wav");

            if (0 == (client.buttons & Defines.BUTTON_ATTACK)) {
                client.getPlayerState().gunframe++;
            } else {
                if (0 == client.pers.inventory[client.ammo_index]) {
                    if (gameExports.level.time >= ent.pain_debounce_time) {
                        gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, gameExports.gameImports
                                .soundindex("weapons/noammo.wav"), 1,
                                Defines.ATTN_NORM, 0);
                        ent.pain_debounce_time = gameExports.level.time + 1;
                    }
                    NoAmmoWeaponChange(ent, gameExports);
                } else {
                    rotation = (float) ((client.getPlayerState().gunframe - 5) * 2
                            * Math.PI / 6);
                    offset[0] = (float) (-4 * Math.sin(rotation));
                    offset[1] = 0f;
                    offset[2] = (float) (4 * Math.cos(rotation));

                    if ((client.getPlayerState().gunframe == 6)
                            || (client.getPlayerState().gunframe == 9))
                        effect = Defines.EF_HYPERBLASTER;
                    else
                        effect = 0;
                    if (gameExports.gameCvars.deathmatch.value != 0)
                        damage = 15;
                    else
                        damage = 20;
                    Blaster_Fire(ent, offset, damage, true, effect, gameExports);
                    if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                        client.pers.inventory[client.ammo_index]--;

                    client.anim_priority = Defines.ANIM_ATTACK;
                    if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                        ent.s.frame = M_Player.FRAME_crattak1 - 1;
                        client.anim_end = M_Player.FRAME_crattak9;
                    } else {
                        ent.s.frame = M_Player.FRAME_attack1 - 1;
                        client.anim_end = M_Player.FRAME_attack8;
                    }
                }

                client.getPlayerState().gunframe++;
                if (client.getPlayerState().gunframe == 12
                        && 0 != client.pers.inventory[client.ammo_index])
                    client.getPlayerState().gunframe = 6;
            }

            if (client.getPlayerState().gunframe == 12) {
                gameExports.gameImports.sound(ent, Defines.CHAN_AUTO, gameExports.gameImports
                        .soundindex("weapons/hyprbd1a.wav"), 1,
                        Defines.ATTN_NORM, 0);
                client.weapon_sound = 0;
            }

            return true;

        }
    };

    public static EntThinkAdapter Weapon_HyperBlaster = new EntThinkAdapter() {
    	public String getID() { return "Weapon_HyperBlaster"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 0 };
            int fire_frames[] = { 6, 7, 8, 9, 10, 11, 0 };

            Weapon_Generic(ent, 5, 20, 49, 53, pause_frames, fire_frames,
                    Weapon_HyperBlaster_Fire, gameExports);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Machinegun = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Machinegun"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 23, 45, 0 };
            int fire_frames[] = { 4, 5, 0 };

            Weapon_Generic(ent, 3, 5, 45, 49, pause_frames, fire_frames,
                    Machinegun_Fire, gameExports);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Chaingun = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Chaingun"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 38, 43, 51, 61, 0 };
            int fire_frames[] = { 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    17, 18, 19, 20, 21, 0 };

            Weapon_Generic(ent, 4, 31, 61, 64, pause_frames, fire_frames,
                    Chaingun_Fire, gameExports);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * SHOTGUN / SUPERSHOTGUN
     * 
     * ======================================================================
     */

    public static EntThinkAdapter weapon_shotgun_fire = new EntThinkAdapter() {
    	public String getID() { return "weapon_shotgun_fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] offset = { 0, 0, 0 };
            int damage = 4;
            int kick = 8;

            gclient_t client = ent.getClient();
            if (client.getPlayerState().gunframe == 9) {
                client.getPlayerState().gunframe++;
                return true;
            }

            Math3D.AngleVectors(client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, client.kick_origin);
            client.kick_angles[0] = -2;

            Math3D.VectorSet(offset, 0, 8, ent.viewheight - 8);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);

            if (gameExports.is_quad) {
                damage *= 4;
                kick *= 4;
            }

            if (gameExports.gameCvars.deathmatch.value != 0)
                GameWeapon.fire_shotgun(ent, start, forward, damage, kick, 500, 500,
                        GameDefines.DEFAULT_DEATHMATCH_SHOTGUN_COUNT,
                        GameDefines.MOD_SHOTGUN, gameExports);
            else
                GameWeapon.fire_shotgun(ent, start, forward, damage, kick, 500, 500,
                        GameDefines.DEFAULT_SHOTGUN_COUNT, GameDefines.MOD_SHOTGUN, gameExports);

            // send muzzle flash

            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_SHOTGUN | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            client.getPlayerState().gunframe++;
            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_Shotgun = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Shotgun"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            int pause_frames[] = { 22, 28, 34, 0 };
            int fire_frames[] = { 8, 9, 0 };

            Weapon_Generic(ent, 7, 18, 36, 39, pause_frames, fire_frames,
                    weapon_shotgun_fire, gameExports);
            return true;
        }
    };

    public static EntThinkAdapter weapon_supershotgun_fire = new EntThinkAdapter() {
    	public String getID() { return "weapon_supershotgun_fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] offset = { 0, 0, 0 };
            float[] v = { 0, 0, 0 };
            int damage = 6;
            int kick = 12;

            gclient_t client = ent.getClient();
            Math3D.AngleVectors(client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, client.kick_origin);
            client.kick_angles[0] = -2;

            Math3D.VectorSet(offset, 0, 8, ent.viewheight - 8);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);

            if (gameExports.is_quad) {
                damage *= 4;
                kick *= 4;
            }

            v[Defines.PITCH] = client.v_angle[Defines.PITCH];
            v[Defines.YAW] = client.v_angle[Defines.YAW] - 5;
            v[Defines.ROLL] = client.v_angle[Defines.ROLL];
            Math3D.AngleVectors(v, forward, null, null);
            GameWeapon.fire_shotgun(ent, start, forward, damage, kick,
                    GameDefines.DEFAULT_SHOTGUN_HSPREAD,
                    GameDefines.DEFAULT_SHOTGUN_VSPREAD,
                    GameDefines.DEFAULT_SSHOTGUN_COUNT / 2, GameDefines.MOD_SSHOTGUN, gameExports);
            v[Defines.YAW] = client.v_angle[Defines.YAW] + 5;
            Math3D.AngleVectors(v, forward, null, null);
            GameWeapon.fire_shotgun(ent, start, forward, damage, kick,
                    GameDefines.DEFAULT_SHOTGUN_HSPREAD,
                    GameDefines.DEFAULT_SHOTGUN_VSPREAD,
                    GameDefines.DEFAULT_SSHOTGUN_COUNT / 2, GameDefines.MOD_SSHOTGUN, gameExports);

            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_SSHOTGUN | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            client.getPlayerState().gunframe++;
            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index] -= 2;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_SuperShotgun = new EntThinkAdapter() {
    	public String getID() { return "Weapon_SuperShotgun"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 29, 42, 57, 0 };
            int fire_frames[] = { 7, 0 };

            Weapon_Generic(ent, 6, 17, 57, 61, pause_frames, fire_frames,
                    weapon_supershotgun_fire, gameExports);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * RAILGUN
     * 
     * ======================================================================
     */
    public static EntThinkAdapter weapon_railgun_fire = new EntThinkAdapter() {
    	public String getID() { return "weapon_railgun_fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] offset = { 0, 0, 0 };
            int damage;
            int kick;

            if (gameExports.gameCvars.deathmatch.value != 0) { // normal damage is too
                // extreme in dm
                damage = 100;
                kick = 200;
            } else {
                damage = 150;
                kick = 250;
            }

            if (gameExports.is_quad) {
                damage *= 4;
                kick *= 4;
            }

            gclient_t client = ent.getClient();
            Math3D.AngleVectors(client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -3, client.kick_origin);
            client.kick_angles[0] = -3;

            Math3D.VectorSet(offset, 0, 7, ent.viewheight - 8);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);
            GameWeapon.fire_rail(ent, start, forward, damage, kick, gameExports);

            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_RAILGUN | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            client.getPlayerState().gunframe++;
            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_Railgun = new EntThinkAdapter() {
    	public String getID() { return "Weapon_Railgun"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int pause_frames[] = { 56, 0 };
            int fire_frames[] = { 4, 0 };
            Weapon_Generic(ent, 3, 18, 56, 61, pause_frames, fire_frames,
                    weapon_railgun_fire, gameExports);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * BFG10K
     * 
     * ======================================================================
     */

    public static EntThinkAdapter weapon_bfg_fire = new EntThinkAdapter() {
    	public String getID() { return "weapon_bfg_fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            float[] offset = { 0, 0, 0 }, start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            int damage;
            float damage_radius = 1000;

            if (gameExports.gameCvars.deathmatch.value != 0)
                damage = 200;
            else
                damage = 500;

            gclient_t client = ent.getClient();
            if (client.getPlayerState().gunframe == 9) {
                gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_BFG | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

                client.getPlayerState().gunframe++;

                PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);
                return true;
            }

            // cells can go down during windup (from power armor hits), so
            // check again and abort firing if we don't have enough now
            if (client.pers.inventory[client.ammo_index] < 50) {
                client.getPlayerState().gunframe++;
                return true;
            }

            if (gameExports.is_quad)
                damage *= 4;

            Math3D.AngleVectors(client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, client.kick_origin);

            // make a big pitch kick with an inverse fall
            client.v_dmg_pitch = -40;
            client.v_dmg_roll = Lib.crandom() * 8;
            client.v_dmg_time = gameExports.level.time + Defines.DAMAGE_TIME;

            Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);
            GameWeapon.fire_bfg(ent, start, forward, damage, 400, damage_radius, gameExports);

            client.getPlayerState().gunframe++;

            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index] -= 50;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_BFG = new EntThinkAdapter() {
    	public String getID() { return "Weapon_BFG"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            int[] pause_frames = { 39, 45, 50, 55, 0 };
            int[] fire_frames = { 9, 17, 0 };

            Weapon_Generic(ent, 8, 32, 55, 58, pause_frames, fire_frames,
                    weapon_bfg_fire, gameExports);
            return true;
        }
    };


    /*
     * ================ 
     * Use_Weapon
     * 
     * Make the weapon ready if there is ammo 
     * ================
     */
    public static ItemUseAdapter Use_Weapon = new ItemUseAdapter() {
    	public String getID() { return "Use_Weapon"; }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            int ammo_index;
            GameItem ammo_item;

            // see if we're already using it
            gclient_t client = ent.getClient();
            if (item == client.pers.weapon)
                return;

            if (item.ammo != null && 0 == gameExports.gameCvars.g_select_empty.value
                    && 0 == (item.flags & GameDefines.IT_AMMO)) {
                
                ammo_item = GameItems.FindItem(item.ammo, gameExports);
                ammo_index = ammo_item.index;

                if (0 == client.pers.inventory[ammo_index]) {
                    gameExports.gameImports.cprintf(ent, Defines.PRINT_HIGH, "No "
                            + ammo_item.pickup_name + " for "
                            + item.pickup_name + ".\n");
                    return;
                }

                if (client.pers.inventory[ammo_index] < item.quantity) {
                    gameExports.gameImports.cprintf(ent, Defines.PRINT_HIGH, "Not enough "
                            + ammo_item.pickup_name + " for "
                            + item.pickup_name + ".\n");
                    return;
                }
            }

            // change to this weapon when down
            client.newweapon = item;
        }
    };

    /*
     * ================ 
     * Drop_Weapon 
     * ================
     */

    public static ItemDropAdapter Drop_Weapon = new ItemDropAdapter() {
        public String getID() {
            return "Drop_Weapon";
        }

        public void drop(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            int index;

            if (0 != ((int) (gameExports.gameCvars.dmflags.value) & Defines.DF_WEAPONS_STAY))
                return;

            index = item.index;
            // see if we're already using it
            gclient_t client = ent.getClient();
            if (((item == client.pers.weapon) || (item == client.newweapon))
                    && (client.pers.inventory[index] == 1)) {
                gameExports.gameImports.cprintf(ent, Defines.PRINT_HIGH,
                        "Can't drop current weapon\n");
                return;
            }

            GameItems.Drop_Item(ent, item, gameExports);
            client.pers.inventory[index]--;
        }
    };

    /*
     * ======================================================================
     * 
     * MACHINEGUN / CHAINGUN
     * 
     * ======================================================================
     */

    public static EntThinkAdapter Machinegun_Fire = new EntThinkAdapter() {
    	public String getID() { return "Machinegun_Fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int i;
            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] angles = { 0, 0, 0 };
            int damage = 8;
            int kick = 2;
            float[] offset = { 0, 0, 0 };

            gclient_t client = ent.getClient();
            if (0 == (client.buttons & Defines.BUTTON_ATTACK)) {
                client.machinegun_shots = 0;
                client.getPlayerState().gunframe++;
                return true;
            }

            if (client.getPlayerState().gunframe == 5)
                client.getPlayerState().gunframe = 4;
            else
                client.getPlayerState().gunframe = 5;

            if (client.pers.inventory[client.ammo_index] < 1) {
                client.getPlayerState().gunframe = 6;
                if (gameExports.level.time >= ent.pain_debounce_time) {
                    gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, gameExports.gameImports
                            .soundindex("weapons/noammo.wav"), 1,
                            Defines.ATTN_NORM, 0);
                    ent.pain_debounce_time = gameExports.level.time + 1;
                }
                NoAmmoWeaponChange(ent, gameExports);
                return true;
            }

            if (gameExports.is_quad) {
                damage *= 4;
                kick *= 4;
            }

            for (i = 1; i < 3; i++) {
                client.kick_origin[i] = Lib.crandom() * 0.35f;
                client.kick_angles[i] = Lib.crandom() * 0.7f;
            }
            client.kick_origin[0] = Lib.crandom() * 0.35f;
            client.kick_angles[0] = client.machinegun_shots * -1.5f;

            // raise the gun as it is firing
            if (0 == gameExports.gameCvars.deathmatch.value) {
                client.machinegun_shots++;
                if (client.machinegun_shots > 9)
                    client.machinegun_shots = 9;
            }

            // get start / end positions
            Math3D
                    .VectorAdd(client.v_angle, client.kick_angles,
                            angles);
            Math3D.AngleVectors(angles, forward, right, null);
            Math3D.VectorSet(offset, 0, 8, ent.viewheight - 8);
            P_ProjectSource(client, ent.s.origin, offset, forward, right,
                    start);
            GameWeapon.fire_bullet(ent, start, forward, damage, kick,
                    GameDefines.DEFAULT_BULLET_HSPREAD,
                    GameDefines.DEFAULT_BULLET_VSPREAD, GameDefines.MOD_MACHINEGUN, gameExports);

            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, Defines.MZ_MACHINEGUN | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index]--;

            client.anim_priority = Defines.ANIM_ATTACK;
            if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                ent.s.frame = M_Player.FRAME_crattak1
                        - (int) (Lib.random() + 0.25);
                client.anim_end = M_Player.FRAME_crattak9;
            } else {
                ent.s.frame = M_Player.FRAME_attack1
                        - (int) (Lib.random() + 0.25);
                client.anim_end = M_Player.FRAME_attack8;
            }
            return true;
        }
    };

    public static EntThinkAdapter Chaingun_Fire = new EntThinkAdapter() {
    	public String getID() { return "Chaingun_Fire"; }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            int i;
            int shots;
            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 }, up = { 0, 0, 0 };
            float r, u;
            float[] offset = { 0, 0, 0 };
            int damage;
            int kick = 2;

            if (gameExports.gameCvars.deathmatch.value != 0)
                damage = 6;
            else
                damage = 8;

            gclient_t client = ent.getClient();
            if (client.getPlayerState().gunframe == 5)
                gameExports.gameImports.sound(ent, Defines.CHAN_AUTO, gameExports.gameImports
                        .soundindex("weapons/chngnu1a.wav"), 1,
                        Defines.ATTN_IDLE, 0);

            if ((client.getPlayerState().gunframe == 14)
                    && 0 == (client.buttons & Defines.BUTTON_ATTACK)) {
                client.getPlayerState().gunframe = 32;
                client.weapon_sound = 0;
                return true;
            } else if ((client.getPlayerState().gunframe == 21)
                    && (client.buttons & Defines.BUTTON_ATTACK) != 0
                    && 0 != client.pers.inventory[client.ammo_index]) {
                client.getPlayerState().gunframe = 15;
            } else {
                client.getPlayerState().gunframe++;
            }

            if (client.getPlayerState().gunframe == 22) {
                client.weapon_sound = 0;
                gameExports.gameImports.sound(ent, Defines.CHAN_AUTO, gameExports.gameImports
                        .soundindex("weapons/chngnd1a.wav"), 1,
                        Defines.ATTN_IDLE, 0);
            } else {
                client.weapon_sound = gameExports.gameImports
                        .soundindex("weapons/chngnl1a.wav");
            }

            client.anim_priority = Defines.ANIM_ATTACK;
            if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                ent.s.frame = M_Player.FRAME_crattak1
                        - (client.getPlayerState().gunframe & 1);
                client.anim_end = M_Player.FRAME_crattak9;
            } else {
                ent.s.frame = M_Player.FRAME_attack1
                        - (client.getPlayerState().gunframe & 1);
                client.anim_end = M_Player.FRAME_attack8;
            }

            if (client.getPlayerState().gunframe <= 9)
                shots = 1;
            else if (client.getPlayerState().gunframe <= 14) {
                if ((client.buttons & Defines.BUTTON_ATTACK) != 0)
                    shots = 2;
                else
                    shots = 1;
            } else
                shots = 3;

            if (client.pers.inventory[client.ammo_index] < shots)
                shots = client.pers.inventory[client.ammo_index];

            if (0 == shots) {
                if (gameExports.level.time >= ent.pain_debounce_time) {
                    gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, gameExports.gameImports
                            .soundindex("weapons/noammo.wav"), 1,
                            Defines.ATTN_NORM, 0);
                    ent.pain_debounce_time = gameExports.level.time + 1;
                }
                NoAmmoWeaponChange(ent, gameExports);
                return true;
            }

            if (gameExports.is_quad) {
                damage *= 4;
                kick *= 4;
            }

            for (i = 0; i < 3; i++) {
                client.kick_origin[i] = Lib.crandom() * 0.35f;
                client.kick_angles[i] = Lib.crandom() * 0.7f;
            }

            for (i = 0; i < shots; i++) {
                // get start / end positions
                Math3D.AngleVectors(client.v_angle, forward, right, up);
                r = 7 + Lib.crandom() * 4;
                u = Lib.crandom() * 4;
                Math3D.VectorSet(offset, 0, r, u + ent.viewheight - 8);
                P_ProjectSource(client, ent.s.origin, offset, forward,
                        right, start);

                GameWeapon.fire_bullet(ent, start, forward, damage, kick,
                        GameDefines.DEFAULT_BULLET_HSPREAD,
                        GameDefines.DEFAULT_BULLET_VSPREAD, GameDefines.MOD_CHAINGUN, gameExports);
            }

            gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, (Defines.MZ_CHAINGUN1 + shots - 1) | gameExports.is_silenced), MulticastTypes.MULTICAST_PVS);

            PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);

            if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
                client.pers.inventory[client.ammo_index] -= shots;

            return true;
        }
    };

    public static EntInteractAdapter Pickup_Weapon = new EntInteractAdapter() {
    	public String getID() { return "Pickup_Weapon"; }
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            int index;
            GameItem ammo;

            index = ent.item.index;

            gclient_t client = other.getClient();
            if ((((int) (gameExports.gameCvars.dmflags.value) & Defines.DF_WEAPONS_STAY) != 0 || gameExports.gameCvars.coop.value != 0)
                    && 0 != client.pers.inventory[index]) {
                if (0 == (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM)))
                    return false; // leave the weapon for others to pickup
            }
    
            client.pers.inventory[index]++;
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)) {
                // give them some ammo with it
                ammo = GameItems.FindItem(ent.item.ammo, gameExports);
                if (((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO) != 0)
                    GameItems.Add_Ammo(other, ammo, 1000);
                else
                    GameItems.Add_Ammo(other, ammo, ammo.quantity);
    
                if (0 == (ent.spawnflags & GameDefines.DROPPED_PLAYER_ITEM)) {
                    if (gameExports.gameCvars.deathmatch.value != 0) {
                        if (((int) (gameExports.gameCvars.dmflags.value) & Defines.DF_WEAPONS_STAY) != 0)
                            ent.flags |= GameDefines.FL_RESPAWN;
                        else
                            GameItems.SetRespawn(ent, 30, gameExports);
                    }
                    if (gameExports.gameCvars.coop.value != 0)
                        ent.flags |= GameDefines.FL_RESPAWN;
                }
            }
    
            if (client.pers.weapon != ent.item
                    && (client.pers.inventory[index] == 1)
                    && (0 == gameExports.gameCvars.deathmatch.value || client.pers.weapon == GameItems
                            .FindItem("blaster", gameExports)))
                client.newweapon = ent.item;
    
            return true;
        }
    };

    public static void P_ProjectSource(gclient_t client, float[] point,
            float[] distance, float[] forward, float[] right, float[] result) {
        float[] _distance = { 0, 0, 0 };

        Math3D.VectorCopy(distance, _distance);
        if (client.pers.hand == Defines.LEFT_HANDED)
            _distance[1] *= -1;
        else if (client.pers.hand == Defines.CENTER_HANDED)
            _distance[1] = 0;
        Math3D.G_ProjectSource(point, _distance, forward, right, result);
    }

    /*
     * =============== 
     * ChangeWeapon
     * 
     * The old weapon has been dropped all the way, so make the new one current
     * ===============
     */
    public static void ChangeWeapon(SubgameEntity ent, GameExportsImpl gameExports) {
        int i;

        gclient_t client = ent.getClient();
        if (client.grenade_time != 0) {
            client.grenade_time = gameExports.level.time;
            client.weapon_sound = 0;
            weapon_grenade_fire(ent, false, gameExports);
            client.grenade_time = 0;
        }

        client.pers.lastweapon = client.pers.weapon;
        client.pers.weapon = client.newweapon;
        client.newweapon = null;
        client.machinegun_shots = 0;

        // set visible model
        if (ent.s.modelindex == 255) {
            if (client.pers.weapon != null)
                i = ((client.pers.weapon.weapmodel & 0xff) << 8);
            else
                i = 0;
            ent.s.skinnum = (ent.index - 1) | i;
        }

        if (client.pers.weapon != null
                && client.pers.weapon.ammo != null)

            client.ammo_index = GameItems
                    .FindItem(client.pers.weapon.ammo, gameExports).index;
        else
            client.ammo_index = 0;

        if (client.pers.weapon == null) { // dead
            client.getPlayerState().gunindex = 0;
            return;
        }

        client.weaponstate = WeaponStates.WEAPON_ACTIVATING;
        client.getPlayerState().gunframe = 0;
        client.getPlayerState().gunindex = gameExports.gameImports
                .modelindex(client.pers.weapon.view_model);

        client.anim_priority = Defines.ANIM_PAIN;
        if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
            ent.s.frame = M_Player.FRAME_crpain1;
            client.anim_end = M_Player.FRAME_crpain4;
        } else {
            ent.s.frame = M_Player.FRAME_pain301;
            client.anim_end = M_Player.FRAME_pain304;

        }
    }

    /*
     * ================= 
     * NoAmmoWeaponChange 
     * =================
     */
    public static void NoAmmoWeaponChange(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t client = ent.getClient();
        if (0 != client.pers.inventory[GameItems
                .FindItem("slugs", gameExports).index]
                && 0 != client.pers.inventory[GameItems
                .FindItem("railgun", gameExports).index]) {
            client.newweapon = GameItems.FindItem("railgun", gameExports);
            return;
        }
        if (0 != client.pers.inventory[GameItems
                .FindItem("cells", gameExports).index]
                && 0 != client.pers.inventory[GameItems
                .FindItem("hyperblaster", gameExports).index]) {
            client.newweapon = GameItems.FindItem("hyperblaster", gameExports);
            return;
        }
        if (0 != client.pers.inventory[GameItems
                .FindItem("bullets", gameExports).index]
                && 0 != client.pers.inventory[GameItems
                .FindItem("chaingun", gameExports).index]) {
            client.newweapon = GameItems.FindItem("chaingun", gameExports);
            return;
        }
        if (0 != client.pers.inventory[GameItems
                .FindItem("bullets", gameExports).index]
                && 0 != client.pers.inventory[GameItems
                .FindItem("machinegun", gameExports).index]) {
            client.newweapon = GameItems.FindItem("machinegun", gameExports);
            return;
        }
        if (client.pers.inventory[GameItems
                .FindItem("shells", gameExports).index] > 1
                && 0 != client.pers.inventory[GameItems
                .FindItem("super shotgun", gameExports).index]) {
            client.newweapon = GameItems.FindItem("super shotgun", gameExports);
            return;
        }
        if (0 != client.pers.inventory[GameItems
                .FindItem("shells", gameExports).index]
                && 0 != client.pers.inventory[GameItems
                .FindItem("shotgun", gameExports).index]) {
            client.newweapon = GameItems.FindItem("shotgun", gameExports);
            return;
        }
        client.newweapon = GameItems.FindItem("blaster", gameExports);
    }

    /*
     * ================= 
     * Think_Weapon
     * 
     * Called by ClientBeginServerFrame and ClientThink 
     * =================
     */
    public static void Think_Weapon(SubgameEntity ent, GameExportsImpl gameExports) {
        // if just died, put the weapon away
        gclient_t client = ent.getClient();
        if (ent.health < 1) {
            client.newweapon = null;
            ChangeWeapon(ent, gameExports);
        }

        // call active weapon think routine
        if (null != client.pers.weapon
                && null != client.pers.weapon.weaponthink) {
            gameExports.is_quad = (client.quad_framenum > gameExports.level.framenum);
            if (client.silencer_shots != 0)
                gameExports.is_silenced = (byte) Defines.MZ_SILENCED;
            else
                gameExports.is_silenced = 0;
            client.pers.weapon.weaponthink.think(ent, gameExports);
        }
    }

    /*
     * ================ 
     * Weapon_Generic
     * 
     * A generic function to handle the basics of weapon thinking
     * ================
     */

    public static void Weapon_Generic(SubgameEntity ent, int FRAME_ACTIVATE_LAST,
                                      int FRAME_FIRE_LAST, int FRAME_IDLE_LAST,
                                      int FRAME_DEACTIVATE_LAST, int pause_frames[], int fire_frames[],
                                      EntThinkAdapter fire, GameExportsImpl gameExports) {
        int FRAME_FIRE_FIRST = (FRAME_ACTIVATE_LAST + 1);
        int FRAME_IDLE_FIRST = (FRAME_FIRE_LAST + 1);
        int FRAME_DEACTIVATE_FIRST = (FRAME_IDLE_LAST + 1);

        int n;

        if (ent.deadflag != 0 || ent.s.modelindex != 255) // VWep animations
        // screw up corpses
        {
            return;
        }

        gclient_t client = ent.getClient();
        if (client.weaponstate == WeaponStates.WEAPON_DROPPING) {
            if (client.getPlayerState().gunframe == FRAME_DEACTIVATE_LAST) {
                ChangeWeapon(ent, gameExports);
                return;
            } else if ((FRAME_DEACTIVATE_LAST - client.getPlayerState().gunframe) == 4) {
                client.anim_priority = Defines.ANIM_REVERSE;
                if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                    ent.s.frame = M_Player.FRAME_crpain4 + 1;
                    client.anim_end = M_Player.FRAME_crpain1;
                } else {
                    ent.s.frame = M_Player.FRAME_pain304 + 1;
                    client.anim_end = M_Player.FRAME_pain301;
                }
            }

            client.getPlayerState().gunframe++;
            return;
        }

        if (client.weaponstate == WeaponStates.WEAPON_ACTIVATING) {
            if (client.getPlayerState().gunframe == FRAME_ACTIVATE_LAST) {
                client.weaponstate = WeaponStates.WEAPON_READY;
                client.getPlayerState().gunframe = FRAME_IDLE_FIRST;
                return;
            }

            client.getPlayerState().gunframe++;
            return;
        }

        if ((client.newweapon != null)
                && (client.weaponstate != WeaponStates.WEAPON_FIRING)) {
            client.weaponstate = WeaponStates.WEAPON_DROPPING;
            client.getPlayerState().gunframe = FRAME_DEACTIVATE_FIRST;

            if ((FRAME_DEACTIVATE_LAST - FRAME_DEACTIVATE_FIRST) < 4) {
                client.anim_priority = Defines.ANIM_REVERSE;
                if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                    ent.s.frame = M_Player.FRAME_crpain4 + 1;
                    client.anim_end = M_Player.FRAME_crpain1;
                } else {
                    ent.s.frame = M_Player.FRAME_pain304 + 1;
                    client.anim_end = M_Player.FRAME_pain301;

                }
            }
            return;
        }

        if (client.weaponstate == WeaponStates.WEAPON_READY) {
            if (((client.latched_buttons | client.buttons) & Defines.BUTTON_ATTACK) != 0) {
                client.latched_buttons &= ~Defines.BUTTON_ATTACK;
                if ((0 == client.ammo_index)
                        || (client.pers.inventory[client.ammo_index] >= client.pers.weapon.quantity)) {
                    client.getPlayerState().gunframe = FRAME_FIRE_FIRST;
                    client.weaponstate = WeaponStates.WEAPON_FIRING;

                    // start the animation
                    client.anim_priority = Defines.ANIM_ATTACK;
                    if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                        ent.s.frame = M_Player.FRAME_crattak1 - 1;
                        client.anim_end = M_Player.FRAME_crattak9;
                    } else {
                        ent.s.frame = M_Player.FRAME_attack1 - 1;
                        client.anim_end = M_Player.FRAME_attack8;
                    }
                } else {
                    if (gameExports.level.time >= ent.pain_debounce_time) {
                        gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, gameExports.gameImports
                                .soundindex("weapons/noammo.wav"), 1,
                                Defines.ATTN_NORM, 0);
                        ent.pain_debounce_time = gameExports.level.time + 1;
                    }
                    NoAmmoWeaponChange(ent, gameExports);
                }
            } else {
                if (client.getPlayerState().gunframe == FRAME_IDLE_LAST) {
                    client.getPlayerState().gunframe = FRAME_IDLE_FIRST;
                    return;
                }

                if (pause_frames != null) {
                    for (n = 0; pause_frames[n] != 0; n++) {
                        if (client.getPlayerState().gunframe == pause_frames[n]) {
                            if ((Lib.rand() & 15) != 0)
                                return;
                        }
                    }
                }

                client.getPlayerState().gunframe++;
                return;
            }
        }

        if (client.weaponstate == WeaponStates.WEAPON_FIRING) {
            for (n = 0; fire_frames[n] != 0; n++) {
                if (client.getPlayerState().gunframe == fire_frames[n]) {
                    if (client.quad_framenum > gameExports.level.framenum)
                        gameExports.gameImports.sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                                .soundindex("items/damage3.wav"), 1,
                                Defines.ATTN_NORM, 0);

                    fire.think(ent, gameExports);
                    break;
                }
            }

            if (0 == fire_frames[n])
                client.getPlayerState().gunframe++;

            if (client.getPlayerState().gunframe == FRAME_IDLE_FIRST + 1)
                client.weaponstate = WeaponStates.WEAPON_READY;
        }
    }

    /*
     * ======================================================================
     * 
     * GRENADE
     * 
     * ======================================================================
     */

    public static void weapon_grenade_fire(SubgameEntity ent, boolean held, GameExportsImpl gameExports) {
        float[] offset = { 0, 0, 0 };
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        float[] start = { 0, 0, 0 };
        int damage = 125;
        float timer;
        int speed;
        float radius;

        radius = damage + 40;
        if (gameExports.is_quad)
            damage *= 4;

        Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
        gclient_t client = ent.getClient();
        Math3D.AngleVectors(client.v_angle, forward, right, null);
        P_ProjectSource(client, ent.s.origin, offset, forward, right, start);

        timer = client.grenade_time - gameExports.level.time;
        speed = (int) (GameDefines.GRENADE_MINSPEED + (GameDefines.GRENADE_TIMER - timer)
                * ((GameDefines.GRENADE_MAXSPEED - GameDefines.GRENADE_MINSPEED) / GameDefines.GRENADE_TIMER));
        GameWeapon.fire_grenade2(ent, start, forward, damage, speed, timer, radius,
                held, gameExports);

        if (0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO))
            client.pers.inventory[client.ammo_index]--;

        client.grenade_time = gameExports.level.time + 1.0f;

        if (ent.deadflag != 0 || ent.s.modelindex != 255) // VWep animations
        // screw up corpses
        {
            return;
        }

        if (ent.health <= 0)
            return;

        if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
            client.anim_priority = Defines.ANIM_ATTACK;
            ent.s.frame = M_Player.FRAME_crattak1 - 1;
            client.anim_end = M_Player.FRAME_crattak3;
        } else {
            client.anim_priority = Defines.ANIM_REVERSE;
            ent.s.frame = M_Player.FRAME_wave08;
            client.anim_end = M_Player.FRAME_wave01;
        }
    }

    /*
     * ======================================================================
     * 
     * BLASTER / HYPERBLASTER
     * 
     * ======================================================================
     */

    public static void Blaster_Fire(SubgameEntity ent, float[] g_offset, int damage,
                                    boolean hyper, int effect, GameExportsImpl gameExports) {
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        float[] start = { 0, 0, 0 };
        float[] offset = { 0, 0, 0 };

        if (gameExports.is_quad)
            damage *= 4;
        gclient_t client = ent.getClient();
        Math3D.AngleVectors(client.v_angle, forward, right, null);
        Math3D.VectorSet(offset, 24, 8, ent.viewheight - 8);
        Math3D.VectorAdd(offset, g_offset, offset);
        P_ProjectSource(client, ent.s.origin, offset, forward, right, start);

        Math3D.VectorScale(forward, -2, client.kick_origin);
        client.kick_angles[0] = -1;

        GameWeapon.fire_blaster(ent, start, forward, damage, 1000, effect, hyper, gameExports);

        // send muzzle flash
        final int flashType;
        if (hyper)
            flashType = Defines.MZ_HYPERBLASTER | gameExports.is_silenced;
        else
            flashType = Defines.MZ_BLASTER | gameExports.is_silenced;

        gameExports.gameImports.multicastMessage(ent.s.origin, new WeaponSoundMessage(ent.index, flashType), MulticastTypes.MULTICAST_PVS);

        PlayerWeapon.PlayerNoise(ent, start, GameDefines.PNOISE_WEAPON, gameExports);
    }

    /*
     * =============== 
     * PlayerNoise
     * 
     * Each player can have two noise objects associated with it: a personal
     * noise (jumping, pain, weapon firing), and a weapon target noise (bullet
     * wall impacts)
     * 
     * Monsters that don't directly see the player can move to a noise in hopes
     * of seeing the player from there. 
     * ===============
     */
    static void PlayerNoise(SubgameEntity who, float[] where, int type, GameExportsImpl gameExports) {

        if (type == GameDefines.PNOISE_WEAPON) {
            gclient_t client = who.getClient();
            if (client.silencer_shots > 0) {
                client.silencer_shots--;
                return;
            }
        }
    
        if (gameExports.gameCvars.deathmatch.value != 0)
            return;
    
        if ((who.flags & GameDefines.FL_NOTARGET) != 0)
            return;

        SubgameEntity noise;
        if (who.mynoise == null) {
            noise = gameExports.G_Spawn();
            noise.classname = "player_noise";
            Math3D.VectorSet(noise.mins, -8, -8, -8);
            Math3D.VectorSet(noise.maxs, 8, 8, 8);
            noise.setOwner(who);
            noise.svflags = Defines.SVF_NOCLIENT;
            who.mynoise = noise;

            noise = gameExports.G_Spawn();
            noise.classname = "player_noise";
            Math3D.VectorSet(noise.mins, -8, -8, -8);
            Math3D.VectorSet(noise.maxs, 8, 8, 8);
            noise.setOwner(who);
            noise.svflags = Defines.SVF_NOCLIENT;
            who.mynoise2 = noise;
        }
    
        if (type == GameDefines.PNOISE_SELF || type == GameDefines.PNOISE_WEAPON) {
            noise = who.mynoise;
            gameExports.level.sound_entity = noise;
            gameExports.level.sound_entity_framenum = gameExports.level.framenum;
        } 
        else // type == PNOISE_IMPACT
        {
            noise = who.mynoise2;
            gameExports.level.sound2_entity = noise;
            gameExports.level.sound2_entity_framenum = gameExports.level.framenum;
        }
    
        Math3D.VectorCopy(where, noise.s.origin);
        Math3D.VectorSubtract(where, noise.maxs, noise.absmin);
        Math3D.VectorAdd(where, noise.maxs, noise.absmax);
        noise.teleport_time = gameExports.level.time;
        gameExports.gameImports.linkentity(noise);
    }
}
