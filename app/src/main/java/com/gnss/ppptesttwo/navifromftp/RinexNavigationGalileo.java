/*
 * Copyright (c) 2011 Eugenio Realini, Mirko Reguzzoni, Cryms sagl - Switzerland. All Rights Reserved.
 *
 * This file is part of goGPS Project (goGPS).
 *
 * goGPS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * goGPS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with goGPS.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.gnss.ppptesttwo.navifromftp;

import android.location.Location;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.gnss.ppptesttwo.Time;
import com.google.location.suplclient.ephemeris.EphemerisResponse;
import com.google.location.suplclient.supl.SuplConnectionRequest;
import com.google.location.suplclient.supl.SuplController;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * @author Lorenzo Patocchi, cryms.com
 *
 * This class retrieve RINEX file on-demand from known server structures
 *
 */
public class RinexNavigationGalileo implements NavigationIono {

    public final static String GARNER_NAVIGATION_AUTO = "ftp://garner.ucsd.edu/pub/nav/${yyyy}/${ddd}/auto${ddd}0.${yy}n.Z";
    public final static String IGN_MULTI_NAVIGATION_DAILY = "ftp://igs.ign.fr/pub/igs/data/campaign/mgex/daily/rinex3/${yyyy}/${ddd}/brdm${ddd}0.${yy}p.Z";
    public final static String GARNER_NAVIGATION_ZIM2 = "ftp://garner.ucsd.edu/pub/nav/${yyyy}/${ddd}/zim2${ddd}0.${yy}n.Z";
    public final static String IGN_NAVIGATION_HOURLY_ZIM2 = "ftp://igs.ensg.ign.fr/pub/igs/data/hourly/${yyyy}/${ddd}/zim2${ddd}${h}.${yy}n.Z";
    public final static String NASA_NAVIGATION_DAILY = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/daily/${yyyy}/${ddd}/${yy}n/brdc${ddd}0.${yy}n.Z";
    public final static String NASA_NAVIGATION_HOURLY = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/hourly/${yyyy}/${ddd}/hour${ddd}0.${yy}n.Z";
    public final static String GARNER_NAVIGATION_AUTO_HTTP = "http://garner.ucsd.edu/pub/rinex/${yyyy}/${ddd}/auto${ddd}0.${yy}n.Z"; // ex http://garner.ucsd.edu/pub/rinex/2016/034/auto0340.16n.Z
    public final static String BKG_GALILEO_RINEX = "ftp://igs.bkg.bund.de/EUREF/BRDC/${yyyy}/${ddd}/BRDC00WRD_R_${yyyy}${ddd}0000_01D_EN.rnx.gz";
    public final static String ESA_GALILEO_RINEX = "ftp://gssc.esa.int/gnss/data/daily/${yyyy}/${ddd}/ankr${ddd}0.${yy}l.Z";



    private final static String TAG = "RinexNavigationGalileo";


    /**
     * cache for negative answers
     */
    private Hashtable<String, Date> negativeChache = new Hashtable<String, Date>();

	/** Folder containing downloaded files */
	public String RNP_CACHE = "./rnp-cache";

    private boolean waitForData = true;

    /**
     * @param args
     */

    /**
     * Template string where to retrieve files on the net
     */
    private String urltemplate;
    private HashMap<String, RinexNavigationParserGalileo> pool = new HashMap<String, RinexNavigationParserGalileo>();



	
	private RinexNavigationParserGalileo rnp;
	
	public BroadcastGGTO getRnpGgto(){
		return rnp.ggto;
	}

	


    public  RinexNavigationParserGalileo getFromFTP(String urltemplate) throws IOException {

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Calendar c = Calendar.getInstance();


        Time t = new Time(c.getTimeInMillis());

        String url = t.formatTemplate(urltemplate);



		RinexNavigationParserGalileo rnp = null;

        String origurl = url;
        if (negativeChache.containsKey(url)) {
            if (System.currentTimeMillis() - negativeChache.get(url).getTime() < 60 * 60 * 1000) {
                throw new FileNotFoundException("cached answer");
            } else {
                negativeChache.remove(url);
            }
        }

        String filename = url.replaceAll("[ ,/:]", "_");
        if (filename.endsWith(".Z")) filename = filename.substring(0, filename.length() - 2);
        if (filename.endsWith(".gz")) filename = filename.substring(0, filename.length() - 3);
        File rnf = new File(RNP_CACHE, filename);

        if (rnf.exists()) {
            System.out.println(url + " from cache file " + rnf);
            rnp = new RinexNavigationParserGalileo(rnf);
            try {
                rnp.init();
                this.rnp=rnp;
                return rnp;
            } catch (Exception e) {
                rnf.delete();
            }
        }

        // if the file doesn't exist of is invalid
        System.out.println(url + " from the net.");
        FTPClient ftp = new FTPClient();

        try {

            Log.w(TAG, "getFromFTP: Getting Galileo data from FTP server...");

            int reply;
            System.out.println("URL: " + url);
            url = url.substring("ftp://".length());
            String server = url.substring(0, url.indexOf('/'));
            String remoteFile = url.substring(url.indexOf('/'));
            String remotePath = remoteFile.substring(0, remoteFile.lastIndexOf('/'));
            remoteFile = remoteFile.substring(remoteFile.lastIndexOf('/') + 1);

            ftp.connect(server);
            ftp.login("anonymous", "");

            System.out.print(ftp.getReplyString());

            // After connection attempt, you should check the reply code to
            // verify
            // success.
            reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                System.err.println("FTP server refused connection.");
                return null;
            }

            ftp.enterLocalPassiveMode();
            ftp.setRemoteVerificationEnabled(false);

            System.out.println("cwd to " + remotePath + " " + ftp.changeWorkingDirectory(remotePath));
            System.out.println(ftp.getReplyString());
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            System.out.println(ftp.getReplyString());


            ftp.setRestartOffset(0); //设置从哪里开始下，就是断点下载
            System.out.println("open " + remoteFile);
            InputStream is = ftp.retrieveFileStream(remoteFile);
            System.out.println(ftp.getReplyString());


            if (ftp.getReplyString().startsWith("230")) {
                negativeChache.put(origurl, new Date());
                throw new FileNotFoundException();
            }
            InputStream uis = is;

            if (remoteFile.endsWith(".Z")) {
                uis = new UncompressInputStream(is);
            }

            if (remoteFile.endsWith(".gz")) {

                uis = new GZIPInputStream(is);
                System.out.println(",,ckdkd");
            }

            rnp = new RinexNavigationParserGalileo(uis, rnf);


            rnp.init();
            this.rnp=rnp;
            is.close();


            ftp.completePendingCommand();

            ftp.logout();

            Log.w(TAG, "getFromFTP: Received Galileo data from server");

        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ioe) {
                    // do nothing
                }
            }
        }
        return rnp;
    }



    @Override
    public IonoGps getIonoGps() {
        return null;
    }

    @Override
    public IonoGalileo getIonoGalileo() {
        return this.rnp.getIonoGalileo();
    }

    public SatellitePosition getSatPositionAndVelocities(long unixTime, double range, int satID, char satType, double receiverClockError) {

        //long unixTime = obs.getRefTime().getMsec();
        //double range = obs.getSatByIDType(satID, satType).getPseudorange(0);

        RinexNavigationParserGalileo rnp = this.rnp;

        if (rnp != null) {
            if (rnp.isTimestampInEpocsRange(unixTime)) {
                return rnp.getSatPositionAndVelocities(unixTime,range , satID, satType, receiverClockError);
            } else {
                return null;
            }
        }

        return null;
    }

}
