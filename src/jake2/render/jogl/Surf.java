/*
 * Surf.java
 * Copyright (C) 2003
 *
 * $Id: Surf.java,v 1.7 2004-01-21 17:08:39 cwei Exp $
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
package jake2.render.jogl;

import java.util.Arrays;

import net.java.games.jogl.GL;
import jake2.Defines;
import jake2.client.dlight_t;
import jake2.client.entity_t;
import jake2.client.lightstyle_t;
import jake2.game.cplane_t;
import jake2.render.glpoly_t;
import jake2.render.image_t;
import jake2.render.medge_t;
import jake2.render.mleaf_t;
import jake2.render.mnode_t;
import jake2.render.model_t;
import jake2.render.msurface_t;
import jake2.render.mtexinfo_t;
import jake2.util.Math3D;

/**
 * Surf
 *  
 * @author cwei
 */
public abstract class Surf extends Draw {

	// GL_RSURF.C: surface-related refresh code
	float[] modelorg = {0, 0, 0};		// relative to viewpoint

	msurface_t	r_alpha_surfaces;

	static final int DYNAMIC_LIGHT_WIDTH = 128;
	static final int DYNAMIC_LIGHT_HEIGHT = 128;

	static final int LIGHTMAP_BYTES = 4;

	static final int BLOCK_WIDTH = 128;
	static final int BLOCK_HEIGHT = 128;

	static final int MAX_LIGHTMAPS = 128;

	int c_visible_lightmaps;
	int c_visible_textures;

	static final int GL_LIGHTMAP_FORMAT = GL.GL_RGBA;

	static class gllightmapstate_t 
	{
		int internal_format;
		int current_lightmap_texture;

		msurface_t[] lightmap_surfaces = new msurface_t[MAX_LIGHTMAPS];

		int[] allocated = new int[BLOCK_WIDTH];

		// the lightmap texture data needs to be kept in
		// main memory so texsubimage can update properly
		byte[] lightmap_buffer = new byte[4*BLOCK_WIDTH*BLOCK_HEIGHT];
		
		public void clearLightmapSurfaces() {
			for (int i = 0; i < MAX_LIGHTMAPS; i++)
				lightmap_surfaces[i] = new msurface_t();
		}
		
	} 

	gllightmapstate_t gl_lms = new gllightmapstate_t();

//
//	static void		LM_InitBlock( void );
//	static void		LM_UploadBlock( qboolean dynamic );
//	static qboolean	LM_AllocBlock (int w, int h, int *x, int *y);
//
//	extern void R_SetCacheState( msurface_t *surf );
//	extern void R_BuildLightMap (msurface_t *surf, byte *dest, int stride);
//

	abstract byte[] Mod_ClusterPVS(int cluster, model_t model);
	abstract void R_DrawSkyBox();
	abstract void R_AddSkySurface(msurface_t surface);
	abstract void R_ClearSkyBox();

	/*
	=============================================================

		BRUSH MODELS

	=============================================================
	*/

	/*
	===============
	R_TextureAnimation

	Returns the proper texture for a given time and base texture
	===============
	*/
	image_t R_TextureAnimation(mtexinfo_t tex)
	{
		int		c;

		if (tex.next == null)
			return tex.image;

		c = currententity.frame % tex.numframes;
		while (c != 0)
		{
			tex = tex.next;
			c--;
		}

		return tex.image;
	}

	/*
	================
	DrawGLPoly
	================
	*/
	void DrawGLPoly(glpoly_t p)
	{
		int i;
		float[] v;

		gl.glBegin(GL.GL_POLYGON);
		for (i=0 ; i<p.numverts ; i++)
		{
			v = p.verts[i];
			gl.glTexCoord2f(v[3], v[4]);
			gl.glVertex3fv(v);
		}
		gl.glEnd();
	}

	//	  ============
	//	  PGM
	/*
	================
	DrawGLFlowingPoly -- version of DrawGLPoly that handles scrolling texture
	================
	*/
//	void DrawGLFlowingPoly (msurface_t *fa)
	void DrawGLFlowingPoly (msurface_t fa)
	{
//		int		i;
//		float	*v;
//		glpoly_t *p;
//		float	scroll;
//
//		p = fa->polys;
//
//		scroll = -64 * ( (r_newrefdef.time / 40.0) - (int)(r_newrefdef.time / 40.0) );
//		if(scroll == 0.0)
//			scroll = -64.0;
//
//		qglBegin (GL_POLYGON);
//		v = p->verts[0];
//		for (i=0 ; i<p->numverts ; i++, v+= VERTEXSIZE)
//		{
//			qglTexCoord2f ((v[3] + scroll), v[4]);
//			qglVertex3fv (v);
//		}
//		qglEnd ();
	}
	//	  PGM
	//	  ============

	/*
	** R_DrawTriangleOutlines
	*/
	void R_DrawTriangleOutlines()
	{
		int i, j;
		glpoly_t	p;

		if (gl_showtris.value == 0)
			return;

		gl.glDisable (GL.GL_TEXTURE_2D);
		gl.glDisable (GL.GL_DEPTH_TEST);
		gl.glColor4f (1,1,1,1);

		for (i=0 ; i<MAX_LIGHTMAPS ; i++)
		{
			msurface_t surf;

			for ( surf = gl_lms.lightmap_surfaces[i]; surf != null; surf = surf.lightmapchain )
			{
				p = surf.polys;
				for ( ; p != null ; p=p.chain)
				{
					for (j=2 ; j<p.numverts ; j++ )
					{
						gl.glBegin (GL.GL_LINE_STRIP);
						gl.glVertex3fv (p.verts[0]);
						gl.glVertex3fv (p.verts[j-1]);
						gl.glVertex3fv (p.verts[j]);
						gl.glVertex3fv (p.verts[0]);
						gl.glEnd ();
					}
				}
			}
		}

		gl.glEnable (GL.GL_DEPTH_TEST);
		gl.glEnable (GL.GL_TEXTURE_2D);
	}

	/*
	** DrawGLPolyChain
	*/
	void DrawGLPolyChain( glpoly_t p, float soffset, float toffset )
	{
//		if ( soffset == 0 && toffset == 0 )
//		{
//			for ( ; p != 0; p = p.chain )
//			{
//				float *v;
//				int j;
//
//				gl.glBegin (GL_POLYGON);
//				v = p.verts[0];
//				for (j=0 ; j<p.numverts ; j++, v+= VERTEXSIZE)
//				{
//					gl.glTexCoord2f (v[5], v[6] );
//					gl.glVertex3fv (v);
//				}
//				gl.glEnd ();
//			}
//		}
//		else
//		{
//			for ( ; p != 0; p = p.chain )
//			{
//				float *v;
//				int j;
//
//				gl.glBegin (GL_POLYGON);
//				v = p.verts[0];
//				for (j=0 ; j<p.numverts ; j++, v+= VERTEXSIZE)
//				{
//					gl.glTexCoord2f (v[5] - soffset, v[6] - toffset );
//					gl.glVertex3fv (v);
//				}
//				gl.glEnd ();
//			}
//		}
	}

	/*
	** R_BlendLightMaps
	**
	** This routine takes all the given light mapped surfaces in the world and
	** blends them into the framebuffer.
	*/
	void R_BlendLightmaps()
	{
		int i;
		msurface_t	surf; 
		msurface_t newdrawsurf = null;

		// don't bother if we're set to fullbright
		if (r_fullbright.value != 0)
			return;
		if (r_worldmodel.lightdata == null)
			return;

		// don't bother writing Z
		gl.glDepthMask( false );

		/*
		** set the appropriate blending mode unless we're only looking at the
		** lightmaps.
		*/
		if (gl_lightmap.value == 0)
		{
			gl.glEnable(GL.GL_BLEND);

			if ( gl_saturatelighting.value != 0)
			{
				gl.glBlendFunc( GL.GL_ONE, GL.GL_ONE );
			}
			else
			{
				char format = gl_monolightmap.string.toUpperCase().charAt(0);
				if ( format != '0' )
				{
					switch ( format )
					{
					case 'I':
						gl.glBlendFunc(GL.GL_ZERO, GL.GL_SRC_COLOR );
						break;
					case 'L':
						gl.glBlendFunc(GL.GL_ZERO, GL.GL_SRC_COLOR );
						break;
					case 'A':
					default:
						gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA );
						break;
					}
				}
				else
				{
					gl.glBlendFunc(GL.GL_ZERO, GL.GL_SRC_COLOR );
				}
			}
		}

		if ( currentmodel == r_worldmodel )
			c_visible_lightmaps = 0;

		/*
		** render static lightmaps first
		*/
		for ( i = 1; i < MAX_LIGHTMAPS; i++ )
		{
			if ( gl_lms.lightmap_surfaces[i] != null )
			{
				if (currentmodel == r_worldmodel)
					c_visible_lightmaps++;
				GL_Bind( gl_state.lightmap_textures + i);

				for ( surf = gl_lms.lightmap_surfaces[i]; surf != null; surf = surf.lightmapchain )
				{
					if ( surf.polys != null )
						DrawGLPolyChain( surf.polys, 0, 0 );
				}
			}
		}

		
		// TODO impl: render dynamic lightmaps

		/*
		** render dynamic lightmaps
		*/
//		if ( gl_dynamic->value )
//		{
//			LM_InitBlock();
//
//			GL_Bind( gl_state.lightmap_textures+0 );
//
//			if (currentmodel == r_worldmodel)
//				c_visible_lightmaps++;
//
//			newdrawsurf = gl_lms.lightmap_surfaces[0];
//
//			for ( surf = gl_lms.lightmap_surfaces[0]; surf != 0; surf = surf->lightmapchain )
//			{
//				int		smax, tmax;
//				byte	*base;
//
//				smax = (surf->extents[0]>>4)+1;
//				tmax = (surf->extents[1]>>4)+1;
//
//				if ( LM_AllocBlock( smax, tmax, &surf->dlight_s, &surf->dlight_t ) )
//				{
//					base = gl_lms.lightmap_buffer;
//					base += ( surf->dlight_t * BLOCK_WIDTH + surf->dlight_s ) * LIGHTMAP_BYTES;
//
//					R_BuildLightMap (surf, base, BLOCK_WIDTH*LIGHTMAP_BYTES);
//				}
//				else
//				{
//					msurface_t *drawsurf;
//
//					// upload what we have so far
//					LM_UploadBlock( true );
//
//					// draw all surfaces that use this lightmap
//					for ( drawsurf = newdrawsurf; drawsurf != surf; drawsurf = drawsurf->lightmapchain )
//					{
//						if ( drawsurf->polys )
//							DrawGLPolyChain( drawsurf->polys, 
//											  ( drawsurf->light_s - drawsurf->dlight_s ) * ( 1.0 / 128.0 ), 
//											( drawsurf->light_t - drawsurf->dlight_t ) * ( 1.0 / 128.0 ) );
//					}
//
//					newdrawsurf = drawsurf;
//
//					// clear the block
//					LM_InitBlock();
//
//					// try uploading the block now
//					if ( !LM_AllocBlock( smax, tmax, &surf->dlight_s, &surf->dlight_t ) )
//					{
//						ri.Sys_Error( ERR_FATAL, "Consecutive calls to LM_AllocBlock(%d,%d) failed (dynamic)\n", smax, tmax );
//					}
//
//					base = gl_lms.lightmap_buffer;
//					base += ( surf->dlight_t * BLOCK_WIDTH + surf->dlight_s ) * LIGHTMAP_BYTES;
//
//					R_BuildLightMap (surf, base, BLOCK_WIDTH*LIGHTMAP_BYTES);
//				}
//			}
//
//			/*
//			** draw remainder of dynamic lightmaps that haven't been uploaded yet
//			*/
//			if ( newdrawsurf )
//				LM_UploadBlock( true );
//
//			for ( surf = newdrawsurf; surf != 0; surf = surf->lightmapchain )
//			{
//				if ( surf->polys )
//					DrawGLPolyChain( surf->polys, ( surf->light_s - surf->dlight_s ) * ( 1.0 / 128.0 ), ( surf->light_t - surf->dlight_t ) * ( 1.0 / 128.0 ) );
//			}
//		}
//
		/*
		** restore state
		*/
		gl.glDisable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDepthMask( true );
	}

	/*
	================
	R_RenderBrushPoly
	================
	*/
	void R_RenderBrushPoly(msurface_t fa)
	{
		int maps;
		image_t image;
		boolean is_dynamic = false;

		c_brush_polys++;

		image = R_TextureAnimation (fa.texinfo);

//		if (fa->flags & SURF_DRAWTURB)
//		{	
//			GL_Bind( image->texnum );
//
//			// warp texture, no lightmaps
//			GL_TexEnv( GL_MODULATE );
//			qglColor4f( gl_state.inverse_intensity, 
//						gl_state.inverse_intensity,
//						gl_state.inverse_intensity,
//						1.0F );
//			EmitWaterPolys (fa);
//			GL_TexEnv( GL_REPLACE );
//
//			return;
//		}
//		else
//		{
//			GL_Bind( image->texnum );
//
//			GL_TexEnv( GL_REPLACE );
//		}
//
////	  ======
////	  PGM
//		if(fa->texinfo->flags & SURF_FLOWING)
//			DrawGLFlowingPoly (fa);
//		else
//			DrawGLPoly (fa->polys);
////	  PGM
////	  ======
//
//		/*
//		** check for lightmap modification
//		*/
//		for ( maps = 0; maps < MAXLIGHTMAPS && fa->styles[maps] != 255; maps++ )
//		{
//			if ( r_newrefdef.lightstyles[fa->styles[maps]].white != fa->cached_light[maps] )
//				goto dynamic;
//		}
//
//		// dynamic this frame or dynamic previously
//		if ( ( fa->dlightframe == r_framecount ) )
//		{
//	dynamic:
//			if ( gl_dynamic->value )
//			{
//				if (!( fa->texinfo->flags & (SURF_SKY|SURF_TRANS33|SURF_TRANS66|SURF_WARP ) ) )
//				{
//					is_dynamic = true;
//				}
//			}
//		}
//
//		if ( is_dynamic )
//		{
//			if ( ( fa->styles[maps] >= 32 || fa->styles[maps] == 0 ) && ( fa->dlightframe != r_framecount ) )
//			{
//				unsigned	temp[34*34];
//				int			smax, tmax;
//
//				smax = (fa->extents[0]>>4)+1;
//				tmax = (fa->extents[1]>>4)+1;
//
//				R_BuildLightMap( fa, (void *)temp, smax*4 );
//				R_SetCacheState( fa );
//
//				GL_Bind( gl_state.lightmap_textures + fa->lightmaptexturenum );
//
//				qglTexSubImage2D( GL_TEXTURE_2D, 0,
//								  fa->light_s, fa->light_t, 
//								  smax, tmax, 
//								  GL_LIGHTMAP_FORMAT, 
//								  GL_UNSIGNED_BYTE, temp );
//
//				fa->lightmapchain = gl_lms.lightmap_surfaces[fa->lightmaptexturenum];
//				gl_lms.lightmap_surfaces[fa->lightmaptexturenum] = fa;
//			}
//			else
//			{
//				fa->lightmapchain = gl_lms.lightmap_surfaces[0];
//				gl_lms.lightmap_surfaces[0] = fa;
//			}
//		}
//		else
//		{
//			fa->lightmapchain = gl_lms.lightmap_surfaces[fa->lightmaptexturenum];
//			gl_lms.lightmap_surfaces[fa->lightmaptexturenum] = fa;
//		}
	}


	/*
	================
	R_DrawAlphaSurfaces

	Draw water surfaces and windows.
	The BSP tree is waled front to back, so unwinding the chain
	of alpha_surfaces will draw back to front, giving proper ordering.
	================
	*/
	void R_DrawAlphaSurfaces()
	{
//		msurface_t	*s;
//		float		intens;
//
//		//
//		// go back to the world matrix
//		//
//		qglLoadMatrixf (r_world_matrix);
//
//		qglEnable (GL_BLEND);
//		GL_TexEnv( GL_MODULATE );
//
//		// the textures are prescaled up for a better lighting range,
//		// so scale it back down
//		intens = gl_state.inverse_intensity;
//
//		for (s=r_alpha_surfaces ; s ; s=s->texturechain)
//		{
//			GL_Bind(s->texinfo->image->texnum);
//			c_brush_polys++;
//			if (s->texinfo->flags & SURF_TRANS33)
//				qglColor4f (intens,intens,intens,0.33);
//			else if (s->texinfo->flags & SURF_TRANS66)
//				qglColor4f (intens,intens,intens,0.66);
//			else
//				qglColor4f (intens,intens,intens,1);
//			if (s->flags & SURF_DRAWTURB)
//				EmitWaterPolys (s);
//			else if(s->texinfo->flags & SURF_FLOWING)			// PGM	9/16/98
//				DrawGLFlowingPoly (s);							// PGM
//			else
//				DrawGLPoly (s->polys);
//		}
//
//		GL_TexEnv( GL_REPLACE );
//		qglColor4f (1,1,1,1);
//		qglDisable (GL_BLEND);
//
//		r_alpha_surfaces = NULL;
	}

	/*
	================
	DrawTextureChains
	================
	*/
	void DrawTextureChains()
	{
//		int		i;
//		msurface_t	*s;
//		image_t		*image;
//
//		c_visible_textures = 0;
//
////		GL_TexEnv( GL_REPLACE );
//
//		if ( !qglSelectTextureSGIS && !qglActiveTextureARB )
//		{
//			for ( i = 0, image=gltextures ; i<numgltextures ; i++,image++)
//			{
//				if (!image->registration_sequence)
//					continue;
//				s = image->texturechain;
//				if (!s)
//					continue;
//				c_visible_textures++;
//
//				for ( ; s ; s=s->texturechain)
//					R_RenderBrushPoly (s);
//
//				image->texturechain = NULL;
//			}
//		}
//		else
//		{
//			for ( i = 0, image=gltextures ; i<numgltextures ; i++,image++)
//			{
//				if (!image->registration_sequence)
//					continue;
//				if (!image->texturechain)
//					continue;
//				c_visible_textures++;
//
//				for ( s = image->texturechain; s ; s=s->texturechain)
//				{
//					if ( !( s->flags & SURF_DRAWTURB ) )
//						R_RenderBrushPoly (s);
//				}
//			}
//
//			GL_EnableMultitexture( false );
//			for ( i = 0, image=gltextures ; i<numgltextures ; i++,image++)
//			{
//				if (!image->registration_sequence)
//					continue;
//				s = image->texturechain;
//				if (!s)
//					continue;
//
//				for ( ; s ; s=s->texturechain)
//				{
//					if ( s->flags & SURF_DRAWTURB )
//						R_RenderBrushPoly (s);
//				}
//
//				image->texturechain = NULL;
//			}
////			GL_EnableMultitexture( true );
//		}
//
//		GL_TexEnv( GL_REPLACE );
	}


	void GL_RenderLightmappedPoly( msurface_t surf )
	{
		int i, nv = surf.polys.numverts;
		int map = 0;
		float[] v;
		image_t image = R_TextureAnimation( surf.texinfo );
		boolean is_dynamic = false;
		int lmtex = surf.lightmaptexturenum;
		glpoly_t p;
//
//		for ( map = 0; map < MAXLIGHTMAPS && surf->styles[map] != 255; map++ )
//		{
//			if ( r_newrefdef.lightstyles[surf->styles[map]].white != surf->cached_light[map] )
//				goto dynamic;
//		}
//
//		// dynamic this frame or dynamic previously
//		if ( ( surf->dlightframe == r_framecount ) )
//		{
//	dynamic:
//			if ( gl_dynamic->value )
//			{
//				if ( !(surf->texinfo->flags & (SURF_SKY|SURF_TRANS33|SURF_TRANS66|SURF_WARP ) ) )
//				{
//					is_dynamic = true;
//				}
//			}
//		}
//
		if ( is_dynamic )
		{
//			unsigned	temp[128*128];
//			int			smax, tmax;
//
			if ( ( (surf.styles[map] & 0xFF) >= 32 || surf.styles[map] == 0 ) && ( surf.dlightframe != r_framecount ) )
			{
//				smax = (surf->extents[0]>>4)+1;
//				tmax = (surf->extents[1]>>4)+1;
//
//				R_BuildLightMap( surf, (void *)temp, smax*4 );
//				R_SetCacheState( surf );
//
//				GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + surf->lightmaptexturenum );
//
//				lmtex = surf->lightmaptexturenum;
//
//				qglTexSubImage2D( GL_TEXTURE_2D, 0,
//								  surf->light_s, surf->light_t, 
//								  smax, tmax, 
//								  GL_LIGHTMAP_FORMAT, 
//								  GL_UNSIGNED_BYTE, temp );
//
			}
			else
			{
//				smax = (surf->extents[0]>>4)+1;
//				tmax = (surf->extents[1]>>4)+1;
//
//				R_BuildLightMap( surf, (void *)temp, smax*4 );
//
//				GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + 0 );
//
//				lmtex = 0;
//
//				qglTexSubImage2D( GL_TEXTURE_2D, 0,
//								  surf->light_s, surf->light_t, 
//								  smax, tmax, 
//								  GL_LIGHTMAP_FORMAT, 
//								  GL_UNSIGNED_BYTE, temp );
//
			}

			c_brush_polys++;

			GL_MBind( GL_TEXTURE0, image.texnum );
			GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + lmtex );

			// ==========
			//	  PGM
			if ((surf.texinfo.flags & Defines.SURF_FLOWING) != 0)
			{
				float scroll;
		
				scroll = -64 * ( (r_newrefdef.time / 40.0f) - (int)(r_newrefdef.time / 40.0f) );
				if(scroll == 0.0f)
					scroll = -64.0f;

				for ( p = surf.polys; p != null; p = p.chain )
				{
					gl.glBegin (GL.GL_POLYGON);
					for (i=0 ; i< nv; i++)
					{
						v = p.verts[i];
						
						gl.glMultiTexCoord2f(GL_TEXTURE0, (v[3] + scroll), v[4]);
						gl.glMultiTexCoord2f(GL_TEXTURE1, v[5], v[6]);
						//gglMTexCoord2fSGIS( GL_TEXTURE0, v[3], v[4]);
						//gglMTexCoord2fSGIS( GL_TEXTURE1, v[5], v[6]);
						gl.glVertex3fv(v);
					}
					gl.glEnd ();
				}
			}
			else
			{
				for ( p = surf.polys; p != null; p = p.chain )
				{
					gl.glBegin (GL.GL_POLYGON);
					for (i=0 ; i< nv; i++)
					{
						v = p.verts[i];
						
						gl.glMultiTexCoord2f(GL_TEXTURE0, v[3], v[4]);
						gl.glMultiTexCoord2f(GL_TEXTURE1, v[5], v[6]);
						//gglMTexCoord2fSGIS( GL_TEXTURE0, v[3], v[4]);
						//gglMTexCoord2fSGIS( GL_TEXTURE1, v[5], v[6]);
						gl.glVertex3fv(v);
					}
					gl.glEnd ();
				}
			}
			// PGM
			// ==========
		}
		else
		{
			c_brush_polys++;

			GL_MBind( GL_TEXTURE0, image.texnum );
			GL_MBind( GL_TEXTURE1, gl_state.lightmap_textures + lmtex );

			// ==========
			//	  PGM
			if ((surf.texinfo.flags & Defines.SURF_FLOWING) != 0)
			{
				float scroll;
		
				scroll = -64 * ( (r_newrefdef.time / 40.0f) - (int)(r_newrefdef.time / 40.0f) );
				if(scroll == 0.0)
					scroll = -64.0f;

				for ( p = surf.polys; p != null; p = p.chain )
				{
					gl.glBegin(GL.GL_POLYGON);
					for (i=0 ; i< nv; i++)
					{
						v = p.verts[i];
			
						gl.glMultiTexCoord2f(GL_TEXTURE0, v[3], v[4]);
						gl.glMultiTexCoord2f(GL_TEXTURE1, v[5], v[6]);
						// qglMTexCoord2fSGIS( GL_TEXTURE0, (v[3]+scroll), v[4]);
						// qglMTexCoord2fSGIS( GL_TEXTURE1, v[5], v[6]);
						gl.glVertex3fv(v);
					}
					gl.glEnd();
				}
			}
			else
			{
			// PGM
			//  ==========
				for ( p = surf.polys; p != null; p = p.chain )
				{
					gl.glBegin (GL.GL_POLYGON);
					for (i=0 ; i< nv; i++)
					{
						v = p.verts[i];
						
						gl.glMultiTexCoord2f(GL_TEXTURE0, v[3], v[4]);
						gl.glMultiTexCoord2f(GL_TEXTURE1, v[5], v[6]);
						//gglMTexCoord2fSGIS( GL_TEXTURE0, v[3], v[4]);
						//gglMTexCoord2fSGIS( GL_TEXTURE1, v[5], v[6]);
						gl.glVertex3fv(v);
					}
					gl.glEnd ();
				}
			// ==========
			// PGM
			}
			// PGM
			// ==========
		}
	}

	/*
	=================
	R_DrawInlineBModel
	=================
	*/
	void R_DrawInlineBModel()
	{
		int i, k;
		cplane_t pplane;
		float dot;
		msurface_t	psurf;
		dlight_t	lt;

		// calculate dynamic lighting for bmodel
		if ( gl_flashblend.value == 0 )
		{
			for (k=0 ; k<r_newrefdef.num_dlights ; k++)
			{
				lt = r_newrefdef.dlights[k];
				// TODO R_MarkLights(lt, 1<<k, currentmodel.nodes[currentmodel.firstnode]);
			}
		}

		// psurf = &currentmodel->surfaces[currentmodel->firstmodelsurface];
		int psurfp = currentmodel.firstmodelsurface;
		msurface_t[] surfaces;
		surfaces = currentmodel.surfaces;
		//psurf = surfaces[psurfp];

		if ( (currententity.flags & Defines.RF_TRANSLUCENT) != 0 )
		{
			gl.glEnable (GL.GL_BLEND);
			gl.glColor4f (1,1,1,0.25f);
			GL_TexEnv( GL.GL_MODULATE );
		}

		//
		// draw texture
		//
		for (i=0 ; i<currentmodel.nummodelsurfaces ; i++)
		{
			psurf = surfaces[psurfp++];
			// find which side of the node we are on
			pplane = psurf.plane;

			dot = Math3D.DotProduct(modelorg, pplane.normal) - pplane.dist;

			// draw the polygon
			if (((psurf.flags & Defines.SURF_PLANEBACK) != 0 && (dot < -BACKFACE_EPSILON)) ||
				((psurf.flags & Defines.SURF_PLANEBACK) == 0 && (dot > BACKFACE_EPSILON)))
			{
				if ((psurf.texinfo.flags & (Defines.SURF_TRANS33 | Defines.SURF_TRANS66)) != 0 )
				{	// add to the translucent chain
					psurf.texturechain = r_alpha_surfaces;
					r_alpha_surfaces = psurf;
				}
				else if ( qglMTexCoord2fSGIS && ( psurf.flags & Defines.SURF_DRAWTURB ) == 0 )
				{
					GL_RenderLightmappedPoly( psurf );
				}
				else
				{
					GL_EnableMultitexture( false );
					R_RenderBrushPoly( psurf );
					GL_EnableMultitexture( true );
				}
			}
		}

		if ( (currententity.flags & Defines.RF_TRANSLUCENT) == 0 )
		{
			if ( !qglMTexCoord2fSGIS )
				R_BlendLightmaps();
		}
		else
		{
			gl.glDisable (GL.GL_BLEND);
			gl.glColor4f (1,1,1,1);
			GL_TexEnv( GL.GL_REPLACE );
		}
	}

	/*
	=================
	R_DrawBrushModel
	=================
	*/
	void R_DrawBrushModel(entity_t e)
	{
		float[] mins = {0, 0, 0};
		float[] maxs = {0, 0, 0};
		int i;
		boolean rotated;

		if (currentmodel.nummodelsurfaces == 0)
			return;

		currententity = e;
		gl_state.currenttextures[0] = gl_state.currenttextures[1] = -1;

		if (e.angles[0] != 0 || e.angles[1] != 0 || e.angles[2] != 0)
		{
			rotated = true;
			for (i=0 ; i<3 ; i++)
			{
				mins[i] = e.origin[i] - currentmodel.radius;
				maxs[i] = e.origin[i] + currentmodel.radius;
			}
		}
		else
		{
			rotated = false;
			Math3D.VectorAdd(e.origin, currentmodel.mins, mins);
			Math3D.VectorAdd(e.origin, currentmodel.maxs, maxs);
		}

		if (R_CullBox(mins, maxs))
			return;

		gl.glColor3f (1,1,1);
		
		// TODO check this: memset (gl_lms.lightmap_surfaces, 0, sizeof(gl_lms.lightmap_surfaces));
		gl_lms.clearLightmapSurfaces();
		
		Math3D.VectorSubtract (r_newrefdef.vieworg, e.origin, modelorg);
		if (rotated)
		{
			float[] temp = {0, 0, 0};
			float[] forward = {0, 0, 0};
			float[] right = {0, 0, 0};
			float[] up = {0, 0, 0};

			Math3D.VectorCopy (modelorg, temp);
			Math3D.AngleVectors (e.angles, forward, right, up);
			modelorg[0] = Math3D.DotProduct (temp, forward);
			modelorg[1] = -Math3D.DotProduct (temp, right);
			modelorg[2] = Math3D.DotProduct (temp, up);
		}

		gl.glPushMatrix();
		
		e.angles[0] = -e.angles[0];	// stupid quake bug
		e.angles[2] = -e.angles[2];	// stupid quake bug
		R_RotateForEntity(e);
		e.angles[0] = -e.angles[0];	// stupid quake bug
		e.angles[2] = -e.angles[2];	// stupid quake bug

		GL_EnableMultitexture( true );
		GL_SelectTexture( GL_TEXTURE0);
		GL_TexEnv( GL.GL_REPLACE );
		GL_SelectTexture( GL_TEXTURE1);
		GL_TexEnv( GL.GL_MODULATE );

		R_DrawInlineBModel();
		GL_EnableMultitexture( false );

		gl.glPopMatrix();
	}

	/*
	=============================================================

		WORLD MODEL

	=============================================================
	*/

	/*
	================
	R_RecursiveWorldNode
	================
	*/
	void R_RecursiveWorldNode (mnode_t node)
	{
		int c, side, sidebit;
		cplane_t plane;
		msurface_t surf;
		msurface_t mark;
		mleaf_t pleaf;
		float dot;
		image_t image;

		if (node.contents == Defines.CONTENTS_SOLID)
			return;		// solid
		
		if (node.visframe != r_visframecount)
			return;
			
		// TODO this is a hack
		if (R_CullBox(node.minmaxs, new float[] {node.minmaxs[3], node.minmaxs[4], node.minmaxs[5]} ))
			return;
	
		// if a leaf node, draw stuff
		if (node.contents != -1)
		{
			pleaf = (mleaf_t)node;

			// check for door connected areas
			if (r_newrefdef.areabits != null)
			{
				if ( ((r_newrefdef.areabits[pleaf.area >> 3] & 0xFF) & (1 << (pleaf.area & 7)) ) == 0 )
					return;		// not visible
			}

			int markp = 1;

			mark = pleaf.getMarkSurface(0); // first marked surface
			c = pleaf.nummarksurfaces;

			if (c != 0)
			{
				do
				{
					mark.visframe = r_framecount;
					mark = pleaf.getMarkSurface(markp++);
				} while (--c != 0);
			}

			return;
		}

		// node is just a decision point, so go down the apropriate sides

		// find which side of the node we are on
		plane = node.plane;

		switch (plane.type)
		{
		case Defines.PLANE_X:
			dot = modelorg[0] - plane.dist;
			break;
		case Defines.PLANE_Y:
			dot = modelorg[1] - plane.dist;
			break;
		case Defines.PLANE_Z:
			dot = modelorg[2] - plane.dist;
			break;
		default:
			dot = Math3D.DotProduct(modelorg, plane.normal) - plane.dist;
			break;
		}

		if (dot >= 0)
		{
			side = 0;
			sidebit = 0;
		}
		else
		{
			side = 1;
			sidebit = Defines.SURF_PLANEBACK;
		}

		// recurse down the children, front side first
		R_RecursiveWorldNode(node.children[side]);

		// draw stuff
		//for ( c = node.numsurfaces, surf = r_worldmodel.surfaces[node.firstsurface]; c != 0 ; c--, surf++)
		for ( c = 0; c < node.numsurfaces; c++)
		{
			surf = r_worldmodel.surfaces[node.firstsurface + c];
			if (surf.visframe != r_framecount)
				continue;

			if ( (surf.flags & Defines.SURF_PLANEBACK) != sidebit )
				continue;		// wrong side

			if ((surf.texinfo.flags & Defines.SURF_SKY) != 0)
			{	// just adds to visible sky bounds
				R_AddSkySurface(surf);
			}
			else if ((surf.texinfo.flags & (Defines.SURF_TRANS33 | Defines.SURF_TRANS66)) != 0)
			{	// add to the translucent chain
				surf.texturechain = r_alpha_surfaces;
				r_alpha_surfaces = surf;
			}
			else
			{
				if ( qglMTexCoord2fSGIS && ( surf.flags & Defines.SURF_DRAWTURB) == 0 )
				{
					GL_RenderLightmappedPoly( surf );
				}
				else
				{
					// the polygon is visible, so add it to the texture
					// sorted chain
					// FIXME: this is a hack for animation
					image = R_TextureAnimation(surf.texinfo);
					surf.texturechain = image.texturechain;
					image.texturechain = surf;
				}
			}
		}

		// recurse down the back side
		R_RecursiveWorldNode(node.children[1 - side]);
	}


	/*
	=============
	R_DrawWorld
	=============
	*/
	void R_DrawWorld()
	{
		entity_t	ent = new entity_t();

		if (r_drawworld.value == 0)
			return;

		if ( (r_newrefdef.rdflags & Defines.RDF_NOWORLDMODEL) != 0 )
			return;

		currentmodel = r_worldmodel;

		Math3D.VectorCopy(r_newrefdef.vieworg, modelorg);

		// auto cycle the world frame for texture animation
		// memset (&ent, 0, sizeof(ent));
		ent.frame = (int)(r_newrefdef.time*2);
		currententity = ent;

		gl_state.currenttextures[0] = gl_state.currenttextures[1] = -1;

		gl.glColor3f (1,1,1);
		// memset (gl_lms.lightmap_surfaces, 0, sizeof(gl_lms.lightmap_surfaces));
		gl_lms.clearLightmapSurfaces();
		
		R_ClearSkyBox();

		if ( qglMTexCoord2fSGIS )
		{
			GL_EnableMultitexture( true );

			GL_SelectTexture( GL_TEXTURE0);
			GL_TexEnv( GL.GL_REPLACE );
			GL_SelectTexture( GL_TEXTURE1);

			if ( gl_lightmap.value != 0)
				GL_TexEnv( GL.GL_REPLACE );
			else 
				GL_TexEnv( GL.GL_MODULATE );

			R_RecursiveWorldNode(r_worldmodel.nodes[0]); // root node

			GL_EnableMultitexture( false );
		}
		else
		{
			R_RecursiveWorldNode(r_worldmodel.nodes[0]); // root node
		}

		/*
		** theoretically nothing should happen in the next two functions
		** if multitexture is enabled
		*/
		DrawTextureChains();
		R_BlendLightmaps();
	
		R_DrawSkyBox();

		R_DrawTriangleOutlines();
	}


	/*
	===============
	R_MarkLeaves

	Mark the leaves and nodes that are in the PVS for the current
	cluster
	===============
	*/
	void R_MarkLeaves()
	{
		byte[] vis;
		byte[] fatvis = new byte[Defines.MAX_MAP_LEAFS / 8];
		mnode_t node;
		int i, c;
		mleaf_t leaf;
		int cluster;

		if (r_oldviewcluster == r_viewcluster && r_oldviewcluster2 == r_viewcluster2 && r_novis.value == 0 && r_viewcluster != -1)
			return;

		// development aid to let you run around and see exactly where
		// the pvs ends
		if (gl_lockpvs.value != 0)
			return;

		r_visframecount++;
		r_oldviewcluster = r_viewcluster;
		r_oldviewcluster2 = r_viewcluster2;

		if (r_novis.value != 0 || r_viewcluster == -1 || r_worldmodel.vis == null)
		{
			// mark everything
			for (i=0 ; i<r_worldmodel.numleafs ; i++)
				r_worldmodel.leafs[i].visframe = r_visframecount;
			for (i=0 ; i<r_worldmodel.numnodes ; i++)
				r_worldmodel.nodes[i].visframe = r_visframecount;
			return;
		}

		vis = Mod_ClusterPVS(r_viewcluster, r_worldmodel);
		// may have to combine two clusters because of solid water boundaries
		if (r_viewcluster2 != r_viewcluster)
		{
			// memcpy (fatvis, vis, (r_worldmodel.numleafs+7)/8);
			System.arraycopy(vis, 0, fatvis, 0, (r_worldmodel.numleafs+7) / 8);
			vis = Mod_ClusterPVS(r_viewcluster2, r_worldmodel);
			c = (r_worldmodel.numleafs + 31)/32;
			for (i=0 ; i<c ; i++) {
				// TODO check this: ((int *)fatvis)[i] |= ((int *)vis)[i];
				fatvis[4 * i + 0] |= vis[4 * i + 0];
				fatvis[4 * i + 1] |= vis[4 * i + 1];
				fatvis[4 * i + 2] |= vis[4 * i + 2];
				fatvis[4 * i + 3] |= vis[4 * i + 3];
			}

			vis = fatvis;
		}
	
		for ( i=0; i < r_worldmodel.numleafs; i++)
		{
			leaf = r_worldmodel.leafs[i];
			cluster = leaf.cluster;
			if (cluster == -1)
				continue;
			if (((vis[cluster>>3] & 0xFF) & (1 << (cluster & 7))) != 0)
			{
				node = (mnode_t)leaf;
				do
				{
					if (node.visframe == r_visframecount)
						break;
					node.visframe = r_visframecount;
					node = node.parent;
				} while (node != null);
			}
		}
	}



	/*
	=============================================================================

	  LIGHTMAP ALLOCATION

	=============================================================================
	*/

	void LM_InitBlock()
	{
		Arrays.fill(gl_lms.allocated, 0);
	}

	void LM_UploadBlock( boolean dynamic )
	{
		int texture;
		int height = 0;

		if ( dynamic )
		{
			texture = 0;
		}
		else
		{
			texture = gl_lms.current_lightmap_texture;
		}

		GL_Bind( gl_state.lightmap_textures + texture );
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);

		if ( dynamic )
		{
			int i;

			for ( i = 0; i < BLOCK_WIDTH; i++ )
			{
				if ( gl_lms.allocated[i] > height )
					height = gl_lms.allocated[i];
			}

			gl.glTexSubImage2D( GL.GL_TEXTURE_2D, 
							  0,
							  0, 0,
							  BLOCK_WIDTH, height,
							  GL_LIGHTMAP_FORMAT,
							  GL.GL_UNSIGNED_BYTE,
							  gl_lms.lightmap_buffer );
		}
		else
		{
			gl.glTexImage2D( GL.GL_TEXTURE_2D, 
						   0, 
						   gl_lms.internal_format,
						   BLOCK_WIDTH, BLOCK_HEIGHT, 
						   0, 
						   GL_LIGHTMAP_FORMAT, 
						   GL.GL_UNSIGNED_BYTE, 
						   gl_lms.lightmap_buffer );
			if ( ++gl_lms.current_lightmap_texture == MAX_LIGHTMAPS )
				ri.Sys_Error( Defines.ERR_DROP, "LM_UploadBlock() - MAX_LIGHTMAPS exceeded\n" );
		}
	}

	// returns a texture number and the position inside it
	boolean LM_AllocBlock (int w, int h, pos_t pos)
	{
		int x = pos.x; 
		int y = pos.y;
		int i, j;
		int best, best2;

		best = BLOCK_HEIGHT;

		for (i=0 ; i<BLOCK_WIDTH-w ; i++)
		{
			best2 = 0;

			for (j=0 ; j<w ; j++)
			{
				if (gl_lms.allocated[i+j] >= best)
					break;
				if (gl_lms.allocated[i+j] > best2)
					best2 = gl_lms.allocated[i+j];
			}
			if (j == w)
			{	// this is a valid spot
				pos.x = i;
				pos.y = best = best2;
			}
		}

		if (best + h > BLOCK_HEIGHT)
			return false;

		for (i=0 ; i<w ; i++)
			gl_lms.allocated[x + i] = best + h;

		return true;
	}

	/*
	================
	GL_BuildPolygonFromSurface
	================
	*/
	void GL_BuildPolygonFromSurface(msurface_t fa)
	{
		int i, lindex, lnumverts;
		medge_t[] pedges;
		medge_t r_pedge;
		int vertpage;
		float[] vec;
		float s, t;
		glpoly_t	poly;
		float[] total = {0, 0, 0};

		// reconstruct the polygon
		pedges = currentmodel.edges;
		lnumverts = fa.numedges;
		vertpage = 0;

		Math3D.VectorClear(total);
		//
		// draw texture
		//
		// poly = Hunk_Alloc (sizeof(glpoly_t) + (lnumverts-4) * VERTEXSIZE*sizeof(float));
		poly = new glpoly_t(lnumverts);

		poly.next = fa.polys;
		poly.flags = fa.flags;
		fa.polys = poly;
		poly.numverts = lnumverts;

		for (i=0 ; i<lnumverts ; i++)
		{
			lindex = currentmodel.surfedges[fa.firstedge + i];

			if (lindex > 0)
			{
				r_pedge = pedges[lindex];
				vec = currentmodel.vertexes[r_pedge.v[0]].position;
			}
			else
			{
				r_pedge = pedges[-lindex];
				vec = currentmodel.vertexes[r_pedge.v[1]].position;
			}
			s = Math3D.DotProduct (vec, fa.texinfo.vecs[0]) + fa.texinfo.vecs[0][3];
			s /= fa.texinfo.image.width;

			t = Math3D.DotProduct (vec, fa.texinfo.vecs[1]) + fa.texinfo.vecs[1][3];
			t /= fa.texinfo.image.height;

			Math3D.VectorAdd (total, vec, total);
			Math3D.VectorCopy (vec, poly.verts[i]);
			poly.verts[i][3] = s;
			poly.verts[i][4] = t;

			//
			// lightmap texture coordinates
			//
			s = Math3D.DotProduct (vec, fa.texinfo.vecs[0]) + fa.texinfo.vecs[0][3];
			s -= fa.texturemins[0];
			s += fa.light_s*16;
			s += 8;
			s /= BLOCK_WIDTH*16; //fa.texinfo.texture.width;

			t = Math3D.DotProduct (vec, fa.texinfo.vecs[1]) + fa.texinfo.vecs[1][3];
			t -= fa.texturemins[1];
			t += fa.light_t*16;
			t += 8;
			t /= BLOCK_HEIGHT*16; //fa.texinfo.texture.height;

			poly.verts[i][5] = s;
			poly.verts[i][6] = t;
		}

		poly.numverts = lnumverts;

	}

	/*
	========================
	GL_CreateSurfaceLightmap
	========================
	*/
	void GL_CreateSurfaceLightmap(msurface_t surf)
	{
		int smax, tmax;
		byte[] base;
//
//		if (surf.flags & (SURF_DRAWSKY|SURF_DRAWTURB))
//			return;
//
//		smax = (surf.extents[0]>>4)+1;
//		tmax = (surf.extents[1]>>4)+1;
//
//		if ( !LM_AllocBlock( smax, tmax, &surf.light_s, &surf.light_t ) )
//		{
//			LM_UploadBlock( false );
//			LM_InitBlock();
//			if ( !LM_AllocBlock( smax, tmax, &surf.light_s, &surf.light_t ) )
//			{
//				ri.Sys_Error( ERR_FATAL, "Consecutive calls to LM_AllocBlock(%d,%d) failed\n", smax, tmax );
//			}
//		}
//
//		surf.lightmaptexturenum = gl_lms.current_lightmap_texture;
//
//		base = gl_lms.lightmap_buffer;
//		base += (surf.light_t * BLOCK_WIDTH + surf.light_s) * LIGHTMAP_BYTES;
//
//		R_SetCacheState( surf );
//		R_BuildLightMap(surf, base, BLOCK_WIDTH*LIGHTMAP_BYTES);
	}

	lightstyle_t[] lightstyles;

	/*
	==================
	GL_BeginBuildingLightmaps

	==================
	*/
	void GL_BeginBuildingLightmaps(model_t m)
	{
		// static lightstyle_t	lightstyles[MAX_LIGHTSTYLES];
		int i;
		int[] dummy = new int[128*128];

		// init lightstyles
		if ( lightstyles == null ) {
			lightstyles = new lightstyle_t[Defines.MAX_LIGHTSTYLES];
			for (i = 0; i < lightstyles.length; i++)
			{
				lightstyles[i] = new lightstyle_t();				
			}
		}

		// memset( gl_lms.allocated, 0, sizeof(gl_lms.allocated) );
		Arrays.fill(gl_lms.allocated, 0);

		r_framecount = 1;		// no dlightcache

		GL_EnableMultitexture( true );
		GL_SelectTexture( GL_TEXTURE1);

		/*
		** setup the base lightstyles so the lightmaps won't have to be regenerated
		** the first time they're seen
		*/
		for (i=0 ; i < Defines.MAX_LIGHTSTYLES ; i++)
		{
			lightstyles[i].rgb[0] = 1;
			lightstyles[i].rgb[1] = 1;
			lightstyles[i].rgb[2] = 1;
			lightstyles[i].white = 3;
		}
		r_newrefdef.lightstyles = lightstyles;

		if (gl_state.lightmap_textures == 0)
		{
			gl_state.lightmap_textures = TEXNUM_LIGHTMAPS;
		}

		gl_lms.current_lightmap_texture = 1;

		/*
		** if mono lightmaps are enabled and we want to use alpha
		** blending (a,1-a) then we're likely running on a 3DLabs
		** Permedia2.  In a perfect world we'd use a GL_ALPHA lightmap
		** in order to conserve space and maximize bandwidth, however 
		** this isn't a perfect world.
		**
		** So we have to use alpha lightmaps, but stored in GL_RGBA format,
		** which means we only get 1/16th the color resolution we should when
		** using alpha lightmaps.  If we find another board that supports
		** only alpha lightmaps but that can at least support the GL_ALPHA
		** format then we should change this code to use real alpha maps.
		*/
		
		char format = gl_monolightmap.string.toUpperCase().charAt(0);
		
		if ( format == 'A' )
		{
			gl_lms.internal_format = gl_tex_alpha_format;
		}
		/*
		** try to do hacked colored lighting with a blended texture
		*/
		else if ( format == 'C' )
		{
			gl_lms.internal_format = gl_tex_alpha_format;
		}
		else if ( format == 'I' )
		{
			gl_lms.internal_format = GL.GL_INTENSITY8;
		}
		else if ( format == 'L' ) 
		{
			gl_lms.internal_format = GL.GL_LUMINANCE8;
		}
		else
		{
			gl_lms.internal_format = gl_tex_solid_format;
		}

		/*
		** initialize the dynamic lightmap texture
		*/
		GL_Bind( gl_state.lightmap_textures + 0 );
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
		gl.glTexImage2D( GL.GL_TEXTURE_2D, 
					   0, 
					   gl_lms.internal_format,
					   BLOCK_WIDTH, BLOCK_HEIGHT, 
					   0, 
					   GL_LIGHTMAP_FORMAT, 
					   GL.GL_UNSIGNED_BYTE, 
					   dummy );
	}

	/*
	=======================
	GL_EndBuildingLightmaps
	=======================
	*/
	void GL_EndBuildingLightmaps()
	{
		LM_UploadBlock( false );
		GL_EnableMultitexture( false );
	}

}
