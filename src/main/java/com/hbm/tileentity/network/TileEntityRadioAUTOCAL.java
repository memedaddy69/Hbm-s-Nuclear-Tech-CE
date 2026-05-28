package com.hbm.tileentity.network;

import com.hbm.config.ServerConfig;
import com.hbm.interfaces.AutoRegister;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.gui.GuiScreenAUTOCAL;
import com.hbm.modules.IParse;
import com.hbm.modules.IParse.*;
import com.hbm.modules.ParseMSES1;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityTickingBase;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@AutoRegister
public class TileEntityRadioAUTOCAL extends TileEntityTickingBase implements IControlReceiver, IGUIProvider {
    public boolean isOn = false;
    public boolean ignoreError = false;
    public boolean autoReboot = false;

    public String stopMessage = "";

    public String[] script = new String[0];
    public IParse msesv1 = new ParseMSES1();
    public ParseContext ctx;

    @Override
    public String getInventoryName() {
        return "container.autocal";
    }

    @Override
    public void update() {
        if (!world.isRemote) {
            if (world.getTotalWorldTime() % 60 == 0) this.markChanged();

            if (this.ctx == null) this.ctx = new ParseContext(world);
            if (this.ctx.world != this.world) this.ctx.world = this.world;

            if (!this.isOn && this.autoReboot) this.isOn = true;

            if (this.isOn) {
                this.stopMessage = "";

                int emergencyBrake = ServerConfig.AUTOCAL_MAX_CLOCK.get() * 5;
                for (int i = 0; i < this.ctx.clockSpeed && emergencyBrake > 0; i++) {
                    emergencyBrake--;

                    if (this.ctx.current == this.script.length) { stop("Program has terminated"); break; }
                    if (this.ctx.current < 0 || this.ctx.current >= this.script.length) { stop("Program index out of bounds"); break; }

                    try {
                        int idx = this.ctx.current;
                        this.ctx.current++;
                        ReturnInfo ret = msesv1.eval(this.ctx, this.script[idx], idx);
                        if (ret.type() == EnumStatementReturn.END_TICK) break;
                        if (ret.type() == EnumStatementReturn.SHUTDOWN) this.stop("Program requested shutdown");

                        if (!this.ignoreError) {
                            String extraInfo = ret.extraInfo().isEmpty() ? "" : ": " + ret.extraInfo();
                            if (ret.type() == EnumStatementReturn.UNRECOGNIZED_COMMAND) this.stop("Unrecognized command at line " + (ret.line() + 1) + extraInfo);
                            if (ret.type() == EnumStatementReturn.PARAMETER_ERROR) this.stop("Parameter error at line " + (ret.line() + 1) + extraInfo);
                            if (ret.type() == EnumStatementReturn.UNDEFINED) this.stop("Undefined behavior at line " + (ret.line() + 1) + extraInfo);
                        }

                        if (ret.type() == EnumStatementReturn.SKIP) i--;
                    } catch (Exception _) {
                        stop("Evaluation unsuccessful");
                    }
                }
            }

            this.networkPackNT(15);
        }
    }

    public void stop(String reason) {
        this.stopMessage = reason;
        this.isOn = false;
        this.ctx.turnOff();
    }

    @Override
    public void serialize(ByteBuf buf) {
        super.serialize(buf);
        buf.writeBoolean(isOn);
        buf.writeBoolean(ignoreError);
        buf.writeBoolean(autoReboot);
        ByteBufUtils.writeUTF8String(buf, this.stopMessage);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        super.deserialize(buf);
        this.isOn = buf.readBoolean();
        this.ignoreError = buf.readBoolean();
        this.autoReboot = buf.readBoolean();
        this.stopMessage = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        this.isOn = nbt.getBoolean("isOn");
        this.ignoreError = nbt.getBoolean("ignoreError");
        this.autoReboot = nbt.getBoolean("autoReboot");

        this.ctx = new ParseContext(null);
        this.ctx.current = nbt.getInteger("current");
        this.ctx.clockSpeed = nbt.getInteger("clockSpeed");
        this.ctx.buffer = nbt.getString("buffer");

        NBTTagList lineList = nbt.getTagList("script", 8);
        this.script = new String[lineList.tagCount()];
        for(int i = 0; i < script.length; i++) {
            this.script[i] = lineList.getStringTagAt(i);
            this.msesv1.generateJumpPoints(ctx, script[i], i);
        }

        this.ctx.variables = nbt.getCompoundTag("variables");
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setBoolean("isOn", isOn);
        nbt.setBoolean("ignoreError", ignoreError);
        nbt.setBoolean("autoReboot", autoReboot);

        nbt.setInteger("current", ctx.current);
        nbt.setInteger("clockSpeed", ctx.clockSpeed);
        nbt.setString("buffer", ctx.buffer);

        NBTTagList lineList = new NBTTagList();
        for(String line : this.script) {
            lineList.appendTag(new NBTTagString(line));
        }
        nbt.setTag("script", lineList);
        nbt.setTag("variables", this.ctx.variables);

        return nbt;
    }

    @Override
    public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public GuiScreen provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return new GuiScreenAUTOCAL(this);
    }

    @Override
    public boolean hasPermission(EntityPlayer player) {
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) <= 15 * 15;
    }

    @Override
    public void receiveControl(NBTTagCompound data) {
        if(data.hasKey("on")) {
            if(this.isOn) stop("User requested shutdown");
            else this.isOn = true;
        }
        if(data.hasKey("ignore")) this.ignoreError = !this.ignoreError;
        if(data.hasKey("auto")) this.autoReboot = !this.autoReboot;

        if(data.hasKey("payload")) {
            this.ctx.jmp.clear();
            this.script = data.getString("payload").split("\n");
            for(int i = 0; i < script.length; i++) {
                script[i] = script[i].trim();
                this.msesv1.generateJumpPoints(ctx, script[i], i);
            }
            if(this.isOn) stop("Script has changed");
        }

        this.markChanged();
    }

    AxisAlignedBB bb = null;

    @Override
    public @NotNull AxisAlignedBB getRenderBoundingBox() {
        if(bb == null) bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        return bb;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }
}
