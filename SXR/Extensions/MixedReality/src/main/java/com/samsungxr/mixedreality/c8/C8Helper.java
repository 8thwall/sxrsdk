/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsungxr.mixedreality.c8;

import android.opengl.Matrix;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Camera;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;

import com.samsungxr.SXRContext;
import com.samsungxr.SXRNode;
import com.samsungxr.mixedreality.SXRAnchor;
import com.samsungxr.mixedreality.SXRMarker;
import com.samsungxr.mixedreality.SXRHitResult;
import com.samsungxr.mixedreality.SXRLightEstimate;
import com.samsungxr.mixedreality.SXRPlane;
import com.samsungxr.mixedreality.SXRTrackingState;
import com.samsungxr.mixedreality.IAnchorEvents;
import com.samsungxr.mixedreality.IMarkerEvents;
import com.samsungxr.mixedreality.IMixedReality;
import com.samsungxr.mixedreality.IPlaneEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class C8Helper
{
    private SXRContext mGvrContext;
    private IMixedReality mMixedReality;
    private Map<Plane, C8Plane> mArPlanes;
    private Map<AugmentedImage, C8Marker> mArAugmentedImages;
    private List<C8Anchor> mArAnchors;

    private Camera mCamera;// C8 camera

    public C8Helper(SXRContext gvrContext, IMixedReality mr) {
        mGvrContext = gvrContext;
        mMixedReality = mr;
        mArPlanes = new HashMap<>();
        mArAugmentedImages = new HashMap<>();
        mArAnchors = new ArrayList<>();
    }


    public void setCamera(Camera camera) {
        this.mCamera = camera;
    }

    public Camera getCamera() {
        return mCamera;
    }

    public void updatePlanes(Collection<Plane> allPlanes, float scale) {

        // Don't update planes (or notify) when the plane listener is empty, i.e., there is
        // no listener registered.
        C8Plane arCorePlane;

        for (Plane plane: allPlanes) {
            if (plane.getTrackingState() != TrackingState.TRACKING
                    || mArPlanes.containsKey(plane)) {
                continue;
            }

            arCorePlane = createPlane(plane);
            // FIXME: New planes are updated two times
            arCorePlane.update(scale);
            notifyPlaneDetectionListeners(arCorePlane);
        }

        for (Plane plane: mArPlanes.keySet()) {
            arCorePlane = mArPlanes.get(plane);

            if (plane.getTrackingState() == TrackingState.TRACKING &&
                    arCorePlane.getTrackingState() != SXRTrackingState.TRACKING) {
                arCorePlane.setTrackingState(SXRTrackingState.TRACKING);
                notifyPlaneStateChangeListeners(arCorePlane, SXRTrackingState.TRACKING);
            }
            else if (plane.getTrackingState() == TrackingState.PAUSED &&
                    arCorePlane.getTrackingState() != SXRTrackingState.PAUSED) {
                arCorePlane.setTrackingState(SXRTrackingState.PAUSED);
                notifyPlaneStateChangeListeners(arCorePlane, SXRTrackingState.PAUSED);
            }
            else if (plane.getTrackingState() == TrackingState.STOPPED &&
                    arCorePlane.getTrackingState() != SXRTrackingState.STOPPED) {
                arCorePlane.setTrackingState(SXRTrackingState.STOPPED);
                notifyPlaneStateChangeListeners(arCorePlane, SXRTrackingState.STOPPED);
            }

            if (plane.getSubsumedBy() != null && arCorePlane.getParentPlane() == null) {
                arCorePlane.setParentPlane(mArPlanes.get(plane.getSubsumedBy()));
                notifyMergedPlane(arCorePlane, arCorePlane.getParentPlane());
            }

            arCorePlane.update(scale);
        }
    }

    public void updateAugmentedImages(Collection<AugmentedImage> allAugmentedImages){
        C8Marker arCoreMarker;

        for (AugmentedImage augmentedImage: allAugmentedImages) {
            if (augmentedImage.getTrackingState() != TrackingState.TRACKING
                || mArAugmentedImages.containsKey(augmentedImage)) {
                continue;
            }

            arCoreMarker = createMarker(augmentedImage);
            notifyMarkerDetectionListeners(arCoreMarker);

            mArAugmentedImages.put(augmentedImage, arCoreMarker);
        }

        for (AugmentedImage augmentedImage: mArAugmentedImages.keySet()) {
            arCoreMarker = mArAugmentedImages.get(augmentedImage);

            if (augmentedImage.getTrackingState() == TrackingState.TRACKING &&
                    arCoreMarker.getTrackingState() != SXRTrackingState.TRACKING) {
                arCoreMarker.setTrackingState(SXRTrackingState.TRACKING);
                notifyMarkerStateChangeListeners(arCoreMarker, SXRTrackingState.TRACKING);
            }
            else if (augmentedImage.getTrackingState() == TrackingState.PAUSED &&
                    arCoreMarker.getTrackingState() != SXRTrackingState.PAUSED) {
                arCoreMarker.setTrackingState(SXRTrackingState.PAUSED);
                notifyMarkerStateChangeListeners(arCoreMarker, SXRTrackingState.PAUSED);
            }
            else if (augmentedImage.getTrackingState() == TrackingState.STOPPED &&
                    arCoreMarker.getTrackingState() != SXRTrackingState.STOPPED) {
                arCoreMarker.setTrackingState(SXRTrackingState.STOPPED);
                notifyMarkerStateChangeListeners(arCoreMarker, SXRTrackingState.STOPPED);
            }
        }
    }

    public void updateAnchors(float scale) {
        for (C8Anchor anchor: mArAnchors) {
            Anchor arAnchor = anchor.getAnchorAR();

            if (arAnchor.getTrackingState() == TrackingState.TRACKING &&
                    anchor.getTrackingState() != SXRTrackingState.TRACKING) {
                anchor.setTrackingState(SXRTrackingState.TRACKING);
                notifyAnchorStateChangeListeners(anchor, SXRTrackingState.TRACKING);
            }
            else if (arAnchor.getTrackingState() == TrackingState.PAUSED &&
                    anchor.getTrackingState() != SXRTrackingState.PAUSED) {
                anchor.setTrackingState(SXRTrackingState.PAUSED);
                notifyAnchorStateChangeListeners(anchor, SXRTrackingState.PAUSED);
            }
            else if (arAnchor.getTrackingState() == TrackingState.STOPPED &&
                    anchor.getTrackingState() != SXRTrackingState.STOPPED) {
                anchor.setTrackingState(SXRTrackingState.STOPPED);
                notifyAnchorStateChangeListeners(anchor, SXRTrackingState.STOPPED);
            }

            anchor.update(scale);
        }
    }

    public ArrayList<SXRPlane> getAllPlanes() {
        ArrayList<SXRPlane> allPlanes = new ArrayList<>();

        for (Plane plane: mArPlanes.keySet()) {
            allPlanes.add(mArPlanes.get(plane));
        }

        return allPlanes;
    }

    public ArrayList<SXRMarker> getAllMarkers() {
        ArrayList<SXRMarker> allAugmentedImages = new ArrayList<>();

        for (AugmentedImage augmentedImage: mArAugmentedImages.keySet()) {
            allAugmentedImages.add(mArAugmentedImages.get(augmentedImage));
        }

        return allAugmentedImages;
    }

    public C8Plane createPlane(Plane plane) {
        C8Plane arCorePlane = new C8Plane(mGvrContext, plane);
        mArPlanes.put(plane, arCorePlane);
        return arCorePlane;
    }

    public C8Marker createMarker(AugmentedImage augmentedImage) {
        C8Marker arCoreMarker = new C8Marker(augmentedImage);
        return arCoreMarker;
    }

    public SXRAnchor createAnchor(Anchor arAnchor, float scale) {
        C8Anchor arCoreAnchor = new C8Anchor(mGvrContext);
        arCoreAnchor.setAnchorAR(arAnchor);
        mArAnchors.add(arCoreAnchor);
        arCoreAnchor.update(scale);
        return arCoreAnchor;
    }

    public void updateAnchorPose(C8Anchor anchor, Anchor arAnchor) {
        if (anchor.getAnchorAR() != null) {
            anchor.getAnchorAR().detach();
        }
        anchor.setAnchorAR(arAnchor);
    }

    public void removeAnchor(C8Anchor anchor) {
        anchor.getAnchorAR().detach();
        mArAnchors.remove(anchor);
        SXRNode anchorNode = anchor.getOwnerObject();
        SXRNode anchorParent = anchorNode.getParent();
        anchorParent.removeChildObject(anchorNode);
    }

    public SXRHitResult hitTest(List<HitResult> hitResult, float scale) {
        for (HitResult hit : hitResult) {
            // Check if any plane was hit, and if it was hit inside the plane polygon
            Trackable trackable = hit.getTrackable();
            // Creates an anchor if a plane or an oriented point was hit.
            if ((trackable instanceof Plane
                    && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                    && ((Plane) trackable).getSubsumedBy() == null) {
                SXRHitResult gvrHitResult = new SXRHitResult();
                float[] hitPose = new float[16];

                hit.getHitPose().toMatrix(hitPose, 0);
                // Convert the value from C8 to SXRf and set the pose
                ar2gvr(hitPose, scale);
                gvrHitResult.setPose(hitPose);
                // TODO: this distance is using C8 values, change it to use SXRf instead
                gvrHitResult.setDistance(hit.getDistance());
                gvrHitResult.setPlane(mArPlanes.get(trackable));

                return gvrHitResult;
            }
        }

        return null;
    }

    /**
     * Converts from AR world space to SXRf world space.
     */
    private void ar2gvr(float[] poseMatrix, float scale) {
        // Real world scale
        Matrix.scaleM(poseMatrix, 0, scale, scale, scale);
        poseMatrix[12] = poseMatrix[12] * scale;
        poseMatrix[13] = poseMatrix[13] * scale;
        poseMatrix[14] = poseMatrix[14] * scale;
    }

    public SXRLightEstimate getLightEstimate(LightEstimate lightEstimate) {
        C8LightEstimate arCoreLightEstimate = new C8LightEstimate();
        SXRLightEstimate.SXRLightEstimateState state;

        arCoreLightEstimate.setPixelIntensity(lightEstimate.getPixelIntensity());
        state = (lightEstimate.getState() == LightEstimate.State.VALID) ?
                SXRLightEstimate.SXRLightEstimateState.VALID :
                SXRLightEstimate.SXRLightEstimateState.NOT_VALID;
        arCoreLightEstimate.setState(state);

        return arCoreLightEstimate;
    }

    private void notifyPlaneDetectionListeners(SXRPlane plane) {
        mGvrContext.getEventManager().sendEvent(mMixedReality,
                IPlaneEvents.class,
                "onPlaneDetected",
                plane);
    }

    private void notifyPlaneStateChangeListeners(SXRPlane plane, SXRTrackingState trackingState) {
        mGvrContext.getEventManager().sendEvent(mMixedReality,
                IPlaneEvents.class,
                "onPlaneStateChange",
                plane,
                trackingState);
    }

    private void notifyMergedPlane(SXRPlane childPlane, SXRPlane parentPlane) {
        mGvrContext.getEventManager().sendEvent(mMixedReality,
                IPlaneEvents.class,
                "onPlaneMerging",
                childPlane,
                parentPlane);
    }

    private void notifyAnchorStateChangeListeners(SXRAnchor anchor, SXRTrackingState trackingState) {
        mGvrContext.getEventManager().sendEvent(mMixedReality,
                IAnchorEvents.class,
                "onAnchorStateChange",
                anchor,
                trackingState);
    }

    private void notifyMarkerDetectionListeners(SXRMarker image) {
        mGvrContext.getEventManager().sendEvent(mMixedReality,
                IMarkerEvents.class,
                "onMarkerDetected",
                image);
    }

    private void notifyMarkerStateChangeListeners(SXRMarker image, SXRTrackingState trackingState) {
        mGvrContext.getEventManager().sendEvent(mMixedReality,
                IMarkerEvents.class,
                "onMarkerStateChange",
                image,
                trackingState);
    }

}
