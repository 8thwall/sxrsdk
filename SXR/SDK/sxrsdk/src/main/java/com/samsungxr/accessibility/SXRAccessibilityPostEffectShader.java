/*
 * Copyright 2015 Samsung Electronics Co., LTD
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.samsungxr.accessibility;

import com.samsungxr.SXRContext;
import com.samsungxr.SXRShader;
import com.samsungxr.SXRShaderTemplate;
import com.samsungxr.R;
import com.samsungxr.utility.TextFile;

/**
 * Shader to invert colors by post processing rendered image from cameras.
 */
public class SXRAccessibilityPostEffectShader  extends SXRShaderTemplate {
    static String fragmentSource;
    static String vertexSource;

    public SXRAccessibilityPostEffectShader(SXRContext context) {
        super("", "sampler2D u_texture", "float3 a_position float2 a_texcoord", SXRShader.GLSLESVersion.VULKAN);
        if (vertexSource == null)
        {
            vertexSource = TextFile.readTextFile(context.getContext(), R.raw.inverted_colors_vertex);
        }
        if (fragmentSource == null)
        {
            fragmentSource = TextFile.readTextFile(context.getContext(), R.raw.inverted_colors_fragment);
        }
        setSegment("VertexTemplate", vertexSource);
        setSegment("FragmentTemplate", fragmentSource);
    }
}
