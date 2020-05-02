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

import android.content.Context;
import android.util.Log;

import com.gnss.ppptesttwo.Time;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

/**
 * @author Lorenzo Patocchi, cryms.com
 * <p>
 * This class retrieve RINEX file on-demand from known server structures
 */
public class RinexNavigationGps  implements NavigationIono {

    public final static String GARNER_NAVIGATION_AUTO = "ftp://garner.ucsd.edu/pub/nav/${yyyy}/${ddd}/auto${ddd}0.${yy}n.Z";
    public final static String IGN_MULTI_NAVIGATION_DAILY = "ftp://igs.ign.fr/pub/igs/data/campaign/mgex/daily/rinex3/${yyyy}/${ddd}/brdm${ddd}0.${yy}p.Z";
    public final static String GARNER_NAVIGATION_ZIM2 = "ftp://garner.ucsd.edu/pub/nav/${yyyy}/${ddd}/zim2${ddd}0.${yy}n.Z";
    public final static String IGN_NAVIGATION_HOURLY_ZIM2 = "ftp://igs.ensg.ign.fr/pub/igs/data/hourly/${yyyy}/${ddd}/zim2${ddd}${h}.${yy}n.Z";
    public final static String NASA_NAVIGATION_DAILY = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/daily/${yyyy}/${ddd}/${yy}n/brdc${ddd}0.${yy}n.Z";
    public final static String NASA_NAVIGATION_HOURLY = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/hourly/${yyyy}/${ddd}/hour${ddd}0.${yy}n.Z";
    public final static String GARNER_NAVIGATION_AUTO_HTTP = "http://garner.ucsd.edu/pub/rinex/${yyyy}/${ddd}/auto${ddd}0.${yy}n.Z"; // ex http://garner.ucsd.edu/pub/rinex/2016/034/auto0340.16n.Z

    public final static String BKG_HOURLY_SUPER_SEVER = "ftp://igs.bkg.bund.de/IGS/BRDC/${yyyy}/${ddd}/brdc${ddd}0.${yy}n.Z";


    private final static String TAG = "RinexNavigationGps";


    /**
     * cache for negative answers
     */
    private Hashtable<String, Date> negativeChache = new Hashtable<String, Date>();


    //这个是url的模板
    private String urltemplate;
    private String url;
    /**
     * Folder containing downloaded files
     */
    public String RNP_CACHE = "./rnp-cache";

    private RinexNavigationParserGps rnp = null;


    public String getUrl() {
        return url;
    }


    public void getFromFTP(String urltemplate) throws IOException {


        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Calendar c = Calendar.getInstance();


        Time t = new Time(c.getTimeInMillis());

        String url = t.formatTemplate(urltemplate);


        RinexNavigationParserGps rnp = null;

        String origurl = url;

        if (negativeChache.containsKey(url)) {
            if (System.currentTimeMillis() - negativeChache.get(url).getTime() < 20 * 60 * 1000) {
            } else {
                negativeChache.remove(url);
                System.out.println("移除");
            }
        }


        String filename = url.replaceAll("[ ,/:]", "_");
        if (filename.endsWith(".Z")) filename = filename.substring(0, filename.length() - 2);


        //File rnf = new File(this.context.getCacheDir(), filename);


        File rnf = new File(RNP_CACHE, filename);
        if (rnf.exists()) {
            System.out.println(url + " from cache file " + rnf);
            try {
                //若文件存在，对其进行读取
                rnp = new RinexNavigationParserGps(rnf);
                rnp.init();
                this.rnp = rnp;
            } catch (Exception e) {
                rnf.delete();
            }
        }

        // if the file doesn't exist of is invalid
        System.out.println(url + " from the net.");
        FTPClient ftp = new FTPClient();

        try {

            Log.w(TAG, "getFromFTP: Getting data from FTP server...");

            int reply;
            System.out.println("URL: " + url);
            url = url.substring("ftp://".length());
            final String server = url.substring(0, url.indexOf('/'));
            System.out.println("sever:" + server);
            String remoteFile = url.substring(url.indexOf('/'));
            String remotePath = remoteFile.substring(0, remoteFile.lastIndexOf('/'));
            remoteFile = remoteFile.substring(remoteFile.lastIndexOf('/') + 1);

            try {
                ftp.connect(server);
                ftp.login("anonymous", "");
            } catch (IOException e) {
                e.printStackTrace();
            }


            //判断是否登录成功  登陆成功以230开头
            System.out.println(ftp.getReplyString());
            if (ftp.getReplyString().startsWith("230")) {
                negativeChache.put(url, new Date());
            }


            // After connection attempt, you should check the reply code to
            // verify
            // success.
            reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                try {
                    ftp.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.err.println("FTP server refused connection.");
            }

            ftp.enterLocalPassiveMode();
            ftp.setRemoteVerificationEnabled(false);


            System.out.println("cwd to " + remotePath + " " + ftp.changeWorkingDirectory(remotePath));
            System.out.println(ftp.getReplyString());

            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            System.out.println(ftp.getReplyString());

            //下载文件

//            OutputStream out = null;
//
//            out = new FileOutputStream(rnf, false);

            ftp.setRestartOffset(0); //设置从哪里开始下，就是断点下载

            InputStream is = null;

            is = ftp.retrieveFileStream(remoteFile);


            System.out.println(ftp.getReplyString());


            InputStream uis = is;


            System.out.println("open " + remoteFile);

            //解压
            if (remoteFile.endsWith(".Z")) {
                uis = new UncompressInputStream(is);
            }
            rnp = new RinexNavigationParserGps(uis, rnf);
            rnp.init();
            this.rnp = rnp;



/*
            //解压之后再下载文件
            byte[] b = new byte[1024];
            int length = 0;
            while ((length = uis.read(b)) != -1) {
                out.write(b, 0, length);
            }

            out.flush();
            out.close();

*/
            is.close();


            ftp.completePendingCommand();

            ftp.logout();


            Log.w(TAG, "getFromFTP: Received data from server");

        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ioe) {
                    // do nothing
                }
            }
        }

    }


    public SatellitePosition getSatPositionAndVelocities(long unixTime, double range, int satID, char satType, double receiverClockError) {

        //long unixTime = obs.getRefTime().getMsec();
        //double range = obs.getSatByIDType(satID, satType).getPseudorange(0);

        RinexNavigationParserGps rnp = this.rnp;

        if (rnp != null) {
            if (rnp.isTimestampInEpocsRange(unixTime)) {
                return rnp.getSatPositionAndVelocities(unixTime,range , satID, satType, receiverClockError);
            } else {
                return null;
            }
        }

        return null;
    }

    @Override
    public IonoGps getIonoGps() {
        return this.rnp.getIonoGps();
    }

    @Override
    public IonoGalileo getIonoGalileo() {
        return null;
    }
}
