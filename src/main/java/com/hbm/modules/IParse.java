package com.hbm.modules;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.HashMap;

public interface IParse {
    ReturnInfo eval(ParseContext ctx, String line, int index);
    void generateJumpPoints(ParseContext ctx, String line, int index);

    class ParseContext {
        public World world;
        public NBTTagCompound variables = new NBTTagCompound();
        public HashMap<String, Integer> jmp = new HashMap<>();

        public String buffer = "";
        public int clockSpeed = 1;
        public int current = 0;

        public ParseContext(World world) {
            this.world = world;
        }

        public void turnOff() {
            this.clockSpeed = 1;
            this.current = 0;
            this.buffer = "";
            if (!this.variables.isEmpty()) this.variables = new NBTTagCompound();
        }
    }

    // Not in 1.7.10 but this does not affect anything except making mses1 easier to debug
    record ReturnInfo(EnumStatementReturn type, int line, String extraInfo) {
        public ReturnInfo(EnumStatementReturn type, int line) {
            this(type, line, "");
        }
    }

    enum EnumStatementReturn {
        /** The command executed correctly (more or less) */
        OK,
        /** The command hasn't been recognized */
        UNRECOGNIZED_COMMAND,
        /** The expected parameters aren't present, or the parameters couldn't be parsed (i.e. using an undefined jump point) */
        PARAMETER_ERROR,
        /** Requests the AUTOCAL unit to end the tick, regardless of how many clock cycles are left */
        END_TICK,
        /** Requests an AUTOCAL shutdown */
        SHUTDOWN,
        /** Skips the instruction, doesn't use up a clock cycle */
        SKIP,
        /** General undefined behavior */
        UNDEFINED
    }
}
