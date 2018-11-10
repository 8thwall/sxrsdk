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

import org.joml.Matrix4f;
import android.support.annotation.NonNull;

import com.google.ar.core.Plane;
import com.google.ar.core.Pose;

import com.samsungxr.SXRContext;
import com.samsungxr.SXRNode;
import com.samsungxr.mixedreality.SXRPlane;
import com.samsungxr.mixedreality.SXRTrackingState;

import java.nio.FloatBuffer;

class C8Plane extends SXRPlane {
    private C8Pose mPose;
    private float mWidth;
    private float mHeight;

    protected C8Plane(SXRContext gvrContext, float width, float height) {
        super(gvrContext);
        mPose = new C8Pose();
        mWidth = width;
        mHeight = height;

        mPlaneType = Type.HORIZONTAL_UPWARD_FACING;
    }

    /**
     * Set the plane tracking state
     *
     * @param state
     */
    protected void setTrackingState(SXRTrackingState state) {
        mTrackingState = state;
    }

    /**
     * Set the parent plane (only when plane is merged)
     *
     * @param plane
     */
    protected void setParentPlane(SXRPlane plane) {
        mParentPlane = plane;
    }

    @Override
    public SXRTrackingState getTrackingState() {
        return mTrackingState;
    }

    @Override
    public void getCenterPose(@NonNull float[] poseOut) {
        if(poseOut.length != 16 ){
            throw new IllegalArgumentException("Array must be 16");
        }
        // Does nothing
    }

    @Override
    public float getWidth() {
        return mWidth;
    }

    @Override
    public float getHeight() {
        return mHeight;
    }

    @Override
    public FloatBuffer getPolygon() {
        FloatBuffer polygon = FloatBuffer.allocate(8);
        float[] polygonPoints = {-mWidth, -mHeight, mWidth, -mHeight, mWidth, mHeight, -mWidth, mHeight};
        polygon.put(polygonPoints);
        return polygon;
    }

    @Override
    public SXRPlane getParentPlane() {
        return mParentPlane;
    }

    @Override
    public boolean isPoseInPolygon(float[] pose) {
        return true;
    }

    /**
     * Update the plane based on arcore best knowledge of the world
     *
     * @param scale
     */
    protected void update(float scale) {
        SXRNode owner = getOwnerObject();
        if (isEnabled() && (owner != null) && owner.isEnabled())
        {
            owner.getTransform().setPosition(0,0,0);
        }
    }

    /**
     * Converts from C8 world space to SXRf's world space.
     *
     * @param scale Scale from AR to SXRf world
     */
    private void convertFromARtoVRSpace(float scale) {
        update(1);
    }
}
