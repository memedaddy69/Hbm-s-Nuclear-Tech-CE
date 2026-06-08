package com.hbm.inventory.gui.element;

import java.util.Iterator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.Tags;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.render.util.NTMBufferBuilder;
import com.hbm.render.util.NTMImmediate;
import com.hbm.util.ColorUtil;
import com.hbm.util.MutableVec3d;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

public class GUIElements {

	@Deprecated public static enum Gauge {
		ROUND_SMALL("small_round", 18, 18, 13);
		ResourceLocation texture;
		int width, height, count;
		private Gauge(String texture, int width, int height, int count) {
			this.texture = new ResourceLocation(Tags.MODID + ":textures/gui/gauges/" + texture + ".png");
			this.width = width;
			this.height = height;
			this.count = count;
		}
	}

	@Deprecated public static void renderGauge(Gauge gauge, double x, double y, double z, double progress) {
		Minecraft.getMinecraft().renderEngine.bindTexture(gauge.texture);
		int frameNum = (int) Math.round((gauge.count - 1) * progress);
		double singleFrame = 1D / (double) gauge.count;
		double frameOffset = singleFrame * frameNum;

		NTMBufferBuilder buf = NTMImmediate.INSTANCE.beginPositionTexQuads(1);
		buf.appendPositionTexQuadUnchecked(
				(float) x, 					(float) (y + gauge.height), (float) z, 0, (float) (frameOffset + singleFrame),
				(float) (x + gauge.width), 	(float) (y + gauge.height), (float) z, 1, (float) (frameOffset + singleFrame),
				(float) (x + gauge.width), 	(float) y, 					(float) z, 1, (float) frameOffset,
				(float) x, 					(float) y, 					(float) z, 0, (float) frameOffset
		);
		NTMImmediate.INSTANCE.draw();
	}

	public static void drawSmoothGauge(int x, int y, double z, double progress, double tipLength, double backLength, double backSide, int color) {
		drawSmoothGauge(x, y, z, progress, tipLength, backLength, backSide, color, 0x000000);
	}

	private static MutableVec3d tip = new MutableVec3d();
	private static MutableVec3d left = new MutableVec3d();
	private static MutableVec3d right = new MutableVec3d();

	public static void drawSmoothGauge(int x, int y, double z, double progress, double tipLength, double backLength, double backSide, int color, int colorOuter) {
		GlStateManager.disableTexture2D();

		progress = MathHelper.clamp(progress, 0, 1);

		float angle = (float) Math.toRadians(-progress * 270 - 45);
		tip.set(0, tipLength, 0);
		left.set(backSide, -backLength, 0);
		right.set(-backSide, -backLength, 0);

		tip.rotateRollSelf(angle);
		left.rotateRollSelf(angle);
		right.rotateRollSelf(angle);

		NTMBufferBuilder buf = NTMImmediate.INSTANCE.beginPositionColor(GL11.GL_TRIANGLES, 6);
		int outerColor = NTMBufferBuilder.packColor(ColorUtil.ir(colorOuter), ColorUtil.ig(colorOuter), ColorUtil.ib(colorOuter), 255);
		int innerColor = NTMBufferBuilder.packColor(ColorUtil.ir(color), ColorUtil.ig(color), ColorUtil.ib(color), 255);
		double mult = 1.5;
		buf.appendPositionColor((float) (x + tip.x * mult), (float) (y + tip.y * mult), (float) z, outerColor);
		buf.appendPositionColor((float) (x + left.x * mult), (float) (y + left.y * mult), (float) z, outerColor);
		buf.appendPositionColor((float) (x + right.x * mult), (float) (y + right.y * mult), (float) z, outerColor);
		buf.appendPositionColor((float) (x + tip.x), (float) (y + tip.y), (float) z, innerColor);
		buf.appendPositionColor((float) (x + left.x), (float) (y + left.y), (float) z, innerColor);
		buf.appendPositionColor((float) (x + right.x), (float) (y + right.y), (float) z, innerColor);
		NTMImmediate.INSTANCE.draw();

		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.enableTexture2D();
	}

	public static final int STANDARD_COLOR_BACKGROUND = -0xFEFFFF0;
	public static final int STANDARD_COLOR_LINE0 = 0x505000FF;
	public static final int STANDARD_COLOR_LINE1 = (STANDARD_COLOR_LINE0 & 0xFEFEFE) >> 1 | STANDARD_COLOR_LINE0 & -0xFEFEFE;
	public static final int RECIPE_COLOR_LINE0 = 0xFFFF8000;
	public static final int RECIPE_COLOR_LINE1 = 0xFFFFFF00;
	public static final int STANDARD_HEADER_OFFSET = 2;
	public static final int STANDARD_LINE_DIST = 10;
	public static void drawHoveringText(List lines, int x, int y, FontRenderer font, RenderItem itemRender, int guiWidth, int guiHeight) {
		drawHoveringText(lines, x, y, font, itemRender, guiWidth, guiHeight, STANDARD_HEADER_OFFSET, STANDARD_LINE_DIST, STANDARD_COLOR_BACKGROUND, STANDARD_COLOR_BACKGROUND, STANDARD_COLOR_LINE0, STANDARD_COLOR_LINE1);
	}
	public static void drawHoveringTextRecipe(List lines, int x, int y, FontRenderer font, RenderItem itemRender, int guiWidth, int guiHeight) {
		drawHoveringText(lines, x, y, font, itemRender, guiWidth, guiHeight, 6, STANDARD_LINE_DIST, STANDARD_COLOR_BACKGROUND, STANDARD_COLOR_BACKGROUND, RECIPE_COLOR_LINE0, RECIPE_COLOR_LINE1);
	}

	public static void drawHoveringTextFluid(List lines, int x, int y, FontRenderer font, RenderItem itemRender, int guiWidth, int guiHeight, FluidType type) {
		int color0 = type.getColor();
		int r = ColorUtil.ir(color0);
		int g = ColorUtil.ig(color0);
		int b = ColorUtil.ib(color0);
		int add = (r + g + b) / 3 > 0x80 ? -0x40 : 0x40;
		int color1 = ColorUtil.color(MathHelper.clamp(r + add, 0, 255), MathHelper.clamp(g + add, 0, 255), MathHelper.clamp(b + add, 0, 255));
		color0 |= 0xff000000;
		color1 |= 0xff000000;
		drawHoveringText(lines, x, y, font, itemRender, guiWidth, guiHeight, 6, STANDARD_LINE_DIST, STANDARD_COLOR_BACKGROUND, STANDARD_COLOR_BACKGROUND, color0, color1);
	}

	public static void drawHoveringText(List lines, int x, int y, FontRenderer font, RenderItem itemRender, int guiWidth, int guiHeight, int headerOffset, int lineDist, int colBG0, int colBG1, int colLine0, int colLine1) {

		if(!lines.isEmpty()) {
			GlStateManager.disableRescaleNormal();
			RenderHelper.disableStandardItemLighting();
			GlStateManager.disableLighting();
			GlStateManager.disableDepth();
			int width = 0;
			Iterator iterator = lines.iterator();

			while(iterator.hasNext()) {
				String line = (String) iterator.next();
				int lineLength = font.getStringWidth(line);

				if(lineLength > width) {
					width = lineLength;
				}
			}

			int boundX = x + 12;
			int boundY = y - 12;
			int height = 6 + headerOffset;

			if(lines.size() > 1) {
				height += 2 + (lines.size() - 1) * lineDist;
			}

			// if trying to leave bottom or right side, move inwards
			if(boundX + width + 4 > guiWidth) boundX -= 28 + width;
			if(boundY + height + 6 > guiHeight) boundY = guiHeight - height - 6;

			// afterwards, see if the tooltip exits the top or left and then fix that, this one's more important and for some fucking reason wasn't handled by vanilla t all
			if(boundX < 4) boundX = 4;
			if(boundY < 4) boundY = 4;

			itemRender.zLevel = 300.0F;
			drawGradientRect(boundX - 3, boundY - 4, boundX + width + 3, boundY - 3, colBG0, colBG0);
			drawGradientRect(boundX - 3, boundY + height + 3, boundX + width + 3, boundY + height + 4, colBG1, colBG1);
			drawGradientRect(boundX - 3, boundY - 3, boundX + width + 3, boundY + height + 3, colBG0, colBG1);
			drawGradientRect(boundX - 4, boundY - 3, boundX - 3, boundY + height + 3, colBG0, colBG1);
			drawGradientRect(boundX + width + 3, boundY - 3, boundX + width + 4, boundY + height + 3, colBG0, colBG1);

			drawGradientRect(boundX - 3, boundY - 3 + 1, boundX - 3 + 1, boundY + height + 3 - 1, colLine0, colLine1);
			drawGradientRect(boundX + width + 2, boundY - 3 + 1, boundX + width + 3, boundY + height + 3 - 1, colLine0, colLine1);
			drawGradientRect(boundX - 3, boundY - 3, boundX + width + 3, boundY - 3 + 1, colLine0, colLine0);
			drawGradientRect(boundX - 3, boundY + height + 2, boundX + width + 3, boundY + height + 3, colLine1, colLine1);

			for(int i = 0; i < lines.size(); ++i) {
				String line = (String) lines.get(i);
				font.drawStringWithShadow(line, boundX, boundY, -1);

				if(i == 0) boundY += headerOffset;
				boundY += lineDist;
			}

			itemRender.zLevel = 0.0F;
			GlStateManager.enableLighting();
			GlStateManager.enableDepth();
			RenderHelper.enableStandardItemLighting();
			GlStateManager.enableRescaleNormal();
		}
	}

	/** Colors don't use the RGBA, but rather ARGB (evil route) */
	protected static void drawGradientRect(int x0, int y0, int x1, int y1, int col0, int col1) {
		int a0 = col0 >> 24 & 255;
		int r0 = col0 >> 16 & 255;
		int g0 = col0 >> 8  & 255;
		int b0 = col0       & 255;
		int a1 = col1 >> 24 & 255;
		int r1 = col1 >> 16 & 255;
		int g1 = col1 >> 8  & 255;
		int b1 = col1       & 255;
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		int color0 = NTMBufferBuilder.packColor(r0, g0, b0, a0);
		int color1 = NTMBufferBuilder.packColor(r1, g1, b1, a1);
		NTMBufferBuilder buf = NTMImmediate.INSTANCE.beginPositionColorQuads(1);
		buf.appendPositionColorUnchecked(x1, y0, 300F, color0);
		buf.appendPositionColorUnchecked(x0, y0, 300F, color0);
		buf.appendPositionColorUnchecked(x0, y1, 300F, color1);
		buf.appendPositionColorUnchecked(x1, y1, 300F, color1);
		NTMImmediate.INSTANCE.draw();
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.disableBlend();
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
	}
}
