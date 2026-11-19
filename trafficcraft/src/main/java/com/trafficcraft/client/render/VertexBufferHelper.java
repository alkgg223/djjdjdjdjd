package com.trafficcraft.client.render;

import com.mojang.blaze3d.vertex.VertexBuffer;

final class VertexBufferHelper {
    static void unbind() {
        VertexBuffer.unbind();
    }
}
