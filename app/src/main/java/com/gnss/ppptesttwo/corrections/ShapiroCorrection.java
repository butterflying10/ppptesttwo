/*
 * Copyright 2018 TFI Systems

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.gnss.ppptesttwo.corrections;

import android.location.Location;


import com.gnss.ppptesttwo.Constants;
import com.gnss.ppptesttwo.Time;
import com.gnss.ppptesttwo.navifromftp.Coordinates;
import com.gnss.ppptesttwo.navifromftp.NavigationIono;
import com.gnss.ppptesttwo.navifromftp.SatellitePosition;

import org.ejml.simple.SimpleMatrix;

/**
 * Created by Sebastian Ciuban on 10/02/2018.
 *
 * Correction for the Shapiro delay that is also known as the relativistic path range correction
 *
 */

public class ShapiroCorrection extends Correction {

    private final static String NAME = "Relativistic path range correction";

    private double correctionValue;

    public ShapiroCorrection(){
        super();
    }

    @Override
    public void calculateCorrection(Time currentTime, Coordinates approximatedPose, SatellitePosition satelliteCoordinates, NavigationIono navigationIono) {
        // Compute the difference vector between the receiver and the satellite
        SimpleMatrix diff = approximatedPose.minusXYZ(satelliteCoordinates);

        // Compute the geometric distance between the receiver and the satellite

        double geomDist = Math.sqrt(Math.pow(diff.get(0), 2) + Math.pow(diff.get(1), 2) + Math.pow(diff.get(2), 2));

        // Compute the geocentric distance of the receiver
        double geoDistRx = Math.sqrt(Math.pow(approximatedPose.getX(), 2) + Math.pow(approximatedPose.getY(), 2) + Math.pow(approximatedPose.getZ(), 2));

        // Compute the geocentric distance of the satellite
        double geoDistSv = Math.sqrt(Math.pow(satelliteCoordinates.getX(), 2) + Math.pow(satelliteCoordinates.getY(), 2) + Math.pow(satelliteCoordinates.getZ(), 2));


        // Compute the shapiro correction
        correctionValue = ((2.0 * Constants.EARTH_GRAVITATIONAL_CONSTANT)/ Math.pow(Constants.SPEED_OF_LIGHT, 2)) * Math.log((geoDistSv + geoDistRx + geomDist ) / (geoDistSv + geoDistRx - geomDist));

    }

    @Override
    public double getCorrection() {
        return correctionValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static void registerClass(){
        register(NAME, ShapiroCorrection.class);
    }
}
