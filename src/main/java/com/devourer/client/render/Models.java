package com.devourer.client.render;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/** Utilitário de cubóides soltos. Mantido só para compatibilidade com arquivos antigos do repositório. */
final class Models {
    static ModelPart box(int u, int v, float x, float y, float z, float w, float h, float d, int texW, int texH) {
        ModelData md = new ModelData();
        md.getRoot().addChild("p", ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, w, h, d), ModelTransform.NONE);
        return TexturedModelData.of(md, texW, texH).createModel().getChild("p");
    }

    static ModelPart box(int u, int v, float x, float y, float z, float w, float h, float d) {
        return box(u, v, x, y, z, w, h, d, 64, 64);
    }

    private Models() {
    }
}
