/*
 * Jake2.java
 * Copyright (C)  2003
 * 
 * $Id: Jake2.java,v 1.2 2003-11-17 22:25:47 hoz Exp $
 */
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
package jake2;

import jake2.qcommon.*;

/**
 * Jake2 is the main class of Quake2 for Java.
 */
public final class Jake2 {

	/**
	 * main is used to start the game. Quake2 for Java supports the 
     * following command line arguments:
	 * @param args
	 */
	public static void main(String[] args) {

		Qcommon.Init(args);
		
		Globals.nostdout = Cvar.Get("nostdout", "0", 0);
		

		long oldtime = System.currentTimeMillis() ;
		long newtime;
		long time;
		while(true) {
			// find time spending rendering last frame
			newtime = System.currentTimeMillis();
			time = newtime - oldtime;

			if (time > 0) Qcommon.Frame(time);
			oldtime = newtime;
		}

	}
}
