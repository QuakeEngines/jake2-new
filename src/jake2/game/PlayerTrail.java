/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

// Created on 13.11.2003 by RST.
// $Id: PlayerTrail.java,v 1.4 2003-12-09 22:12:43 rst Exp $

package jake2.game;

import jake2.util.*;
import jake2.util.*;

public class PlayerTrail extends Game {
	
	/*
	==============================================================================
	
	PLAYER TRAIL
	
	==============================================================================
	
	This is a circular list containing the a list of points of where
	the player has been recently.  It is used by monsters for pursuit.
	
	.origin		the spot
	.owner		forward link
	.aiment		backward link
	*/

	static int TRAIL_LENGTH= 8;

	static edict_t trail[]= new edict_t[TRAIL_LENGTH];
	static int trail_head;
	static boolean trail_active= false;

	static int NEXT(int n) {
		return (n + 1) % TRAIL_LENGTH;
	}

	static int PREV(int n) {
		return (n + TRAIL_LENGTH - 1) % TRAIL_LENGTH;
	}

	static void Init() {

		// FIXME || coop 
		if (deathmatch.value != 0)
			return;

		for (int n= 0; n < TRAIL_LENGTH; n++) {
			trail[n]= G_Spawn();
			trail[n].classname= "player_trail";
		}

		trail_head= 0;
		trail_active= true;
	}

	static void Add(float[] spot) {
		float[] temp= { 0, 0, 0 };

		if (!trail_active)
			return;

		Math3D.VectorCopy(spot, trail[trail_head].s.origin);

		trail[trail_head].timestamp= level.time;

		Math3D.VectorSubtract(spot, trail[PREV(trail_head)].s.origin, temp);
		trail[trail_head].s.angles[1]= Math3D.vectoyaw(temp);

		trail_head= NEXT(trail_head);
	}

	static void New(float[] spot) {
		if (!trail_active)
			return;

		Init();
		Add(spot);
	}

	static edict_t PickFirst(edict_t self) {

		if (!trail_active)
			return null;

		int marker= trail_head;

		for (int n= TRAIL_LENGTH; n > 0; n--) {
			if (trail[marker].timestamp <= self.monsterinfo.trail_time)
				marker= NEXT(marker);
			else
				break;
		}

		if (visible(self, trail[marker])) {
			return trail[marker];
		}

		if (visible(self, trail[PREV(marker)])) {
			return trail[PREV(marker)];
		}

		return trail[marker];
	}

	static edict_t PickNext(edict_t self) {
		int marker;
		int n;

		if (!trail_active)
			return null;

		for (marker= trail_head, n= TRAIL_LENGTH; n > 0; n--) {
			if (trail[marker].timestamp <= self.monsterinfo.trail_time)
				marker= NEXT(marker);
			else
				break;
		}

		return trail[marker];
	}

	static edict_t LastSpot() {
		return trail[PREV(trail_head)];
	}


}
