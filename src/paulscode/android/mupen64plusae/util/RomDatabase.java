/**
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2013 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Mupen64PlusAE. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * Authors: littleguy77
 */
package paulscode.android.mupen64plusae.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang.NullArgumentException;

import paulscode.android.mupen64plusae.persistent.ConfigFile;
import paulscode.android.mupen64plusae.persistent.ConfigFile.ConfigSection;
import android.text.TextUtils;
import android.util.Log;

public class RomDatabase
{
    private static final String ART_URL_TEMPLATE = "http://paulscode.com/downloads/Mupen64Plus-AE/CoverArt/%s";
    private static final String WIKI_URL_TEMPLATE = "http://littleguy77.wikia.com/wiki/%s";
    
    private ConfigFile mConfigFile = null;
    private final HashMap<String, ArrayList<ConfigSection>> mCrcMap = new HashMap<String, ArrayList<ConfigSection>>();
    
    public RomDatabase( String mupen64plusIni )
    {
        mConfigFile = new ConfigFile( mupen64plusIni );
        for( String key : mConfigFile.keySet() )
        {
            ConfigSection section = mConfigFile.get( key );
            if( section != null )
            {
                String crc = section.get( "CRC" );
                if( crc != null )
                {
                    if( mCrcMap.get( crc ) == null )
                        mCrcMap.put( crc, new ArrayList<ConfigSection>() );
                    mCrcMap.get( crc ).add( section );
                }
            }
        }
    }
    
    public RomDetail lookupByMd5WithFallback( String md5, File file )
    {
        RomDetail detail = lookupByMd5( md5 );
        if( detail == null )
        {
            // MD5 not in the database; lookup by CRC instead
            String crc = new RomHeader( file ).crc;
            RomDetail[] romDetails = lookupByCrc( crc );
            if( romDetails.length == 0 )
            {
                // CRC not in the database; create best guess
                Log.w( "RomDetail", "No meta-info entry found for ROM " + file.getAbsolutePath() );
                Log.i( "RomDetail", "Constructing a best guess for the meta-info" );
                String goodName = file.getName().split( "\\." )[0];
                detail = new RomDetail( crc, goodName );
            }
            else if( romDetails.length > 1 )
            {
                // CRC in the database more than once; let user pick best match
                // TODO Implement popup selector
                Log.w( "RomDetail", "Multiple meta-info entries found for ROM " + file.getAbsolutePath() );
                Log.i( "RomDetail", "Defaulting to first entry" );
                detail = romDetails[0];
            }
            else
            {
                // CRC in the database exactly once; use it
                detail = romDetails[0];
            }
        }
        return detail;
    }
    
    public RomDetail lookupByMd5( String md5 )
    {
        ConfigSection section = mConfigFile.get( md5 );
        return section == null ? null : new RomDetail( section );
    }
    
    public RomDetail[] lookupByCrc( String crc )
    {
        ArrayList<ConfigSection> sections = mCrcMap.get( crc );
        if( sections == null )
            return new RomDetail[0];
        
        RomDetail[] results = new RomDetail[sections.size()];
        for( int i = 0; i < results.length; i++ )
            results[i] = new RomDetail( sections.get( i ) );
        return results;
    }
    
    public class RomDetail
    {
        public final String crc;
        public final String goodName;
        public final String baseName;
        public final String artName;
        public final String artUrl;
        public final String wikiUrl;
        public final String saveType;
        public final int status;
        public final int players;
        public final boolean rumble;
        
        private RomDetail( ConfigSection section )
        {
            // Never pass a null section
            if( section == null )
                throw new NullArgumentException( "section" );
            
            crc = section.get( "CRC" );
            
            // Use an empty goodname (not null) for certain homebrew ROMs
            if( "00000000 00000000".equals( crc ) )
                goodName = "";
            else
                goodName = section.get( "GoodName" );
            
            if( goodName != null )
            {
                // Extract basename (goodname without the extra parenthetical tags)
                baseName = goodName.split( " \\(" )[0].trim();
                
                // Generate the cover art URL string
                artName = baseName.replaceAll( "['\\.]", "" ).replaceAll( "\\W+", "_" ) + ".png";
                artUrl = String.format( ART_URL_TEMPLATE, artName );
                
                // Generate wiki page URL string
                String _wikiUrl = null;
                _wikiUrl = String.format( WIKI_URL_TEMPLATE, baseName.replaceAll( " ", "_" ) );
                if( goodName.contains( "(Kiosk" ) )
                    _wikiUrl += "_(Kiosk_Demo)";
                wikiUrl = _wikiUrl;
            }
            else
            {
                Log.e( "RomDetail.ctor",
                        "mupen64plus.ini appears to be corrupt.  GoodName field is not defined for selected ROM." );
                baseName = null;
                artName = null;
                artUrl = null;
                wikiUrl = null;
            }
            
            // Some ROMs have multiple entries. Instead of duplicating common data, the ini file
            // just references another entry.
            String refMd5 = section.get( "RefMD5" );
            if( !TextUtils.isEmpty( refMd5 ) )
                section = mConfigFile.get( refMd5 );
            
            if( section != null )
            {
                saveType = section.get( "SaveType" );
                String statusString = section.get( "Status" );
                String playersString = section.get( "Players" );
                status = TextUtils.isEmpty( statusString ) ? 0 : Integer.parseInt( statusString );
                players = TextUtils.isEmpty( playersString ) ? 0 : Integer.parseInt( playersString );
                rumble = "Yes".equals( section.get( "Rumble" ) );
            }
            else
            {
                Log.e( "RomDetail.ctor",
                        "mupen64plus.ini appears to be corrupt.  RefMD5 field does not refer to a known ROM." );
                saveType = null;
                status = 0;
                players = 4;
                rumble = true;
            }
        }
        
        private RomDetail( String assumedCrc, String assumedGoodName )
        {
            // Never pass null arguments
            if( assumedCrc == null )
                throw new NullArgumentException( "assumedCrc" );
            if( assumedGoodName == null )
                throw new NullArgumentException( "assumedGoodName" );
            
            crc = assumedCrc;
            goodName = assumedGoodName;
            baseName = goodName.split( " \\(" )[0].trim();
            artName = null;
            artUrl = null;
            wikiUrl = null;
            saveType = null;
            status = 0;
            players = 4;
            rumble = true;
        }
    }
}
