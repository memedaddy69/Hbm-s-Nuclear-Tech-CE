package com.hbm.modules;

import com.hbm.config.ServerConfig;
import com.hbm.tileentity.network.RTTYSystem;
import com.hbm.util.Calculator;

import java.util.Locale;

public class ParseMSES1 implements IParse {
    @Override
    public ReturnInfo eval(ParseContext ctx, String line, int index) {
        String lower = line.toLowerCase(Locale.US);

        // jump point destination, skip
        if (lower.startsWith("dest ") || lower.startsWith("# ") || lower.isBlank()) {
            return new ReturnInfo(EnumStatementReturn.SKIP, index);
        }

        // no operation, still eats up a clock cycle
        if (lower.equals("nop")) {
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // sets the desired clock speed, then skips the operation
        if (lower.startsWith("clockspeed ")) {
            if (line.length() <= 11) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            try {
                int speed = Integer.parseInt(line.substring(11));
                if (speed < 1 || speed > ServerConfig.AUTOCAL_MAX_CLOCK.get()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Invalid clock speed");
                ctx.clockSpeed = speed;
            } catch (Throwable _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Not a valid number"); }
            return new ReturnInfo(EnumStatementReturn.SKIP, index);
        }

        // sets the script index to the jump point
        if (lower.startsWith("jmp ")) {
            if (line.length() <= 4) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            String jmpKey = substitute(ctx, line.substring(4));
            if (ctx.jmp.containsKey(jmpKey)) {
                ctx.current = ctx.jmp.get(jmpKey);
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }
            return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Could not find jump destination");
        }

        // sets the script index to the jump point, if the buffer is the string 'true'
        if (lower.startsWith("jmpif ")) {
            if (line.length() <= 6) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (!ctx.buffer.equals("true")) return new ReturnInfo(EnumStatementReturn.OK, index);
            String jmpKey = substitute(ctx, line.substring(6));
            if (ctx.jmp.containsKey(jmpKey)) {
                ctx.current = ctx.jmp.get(jmpKey);
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }
            return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Could not find jump destination");
        }

        // sets the script index to the jump point, if the buffer is the NOT 'true'
        if (lower.startsWith("jmpnot ")) {
            if (line.length() <= 7) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.equals("true")) return new ReturnInfo(EnumStatementReturn.OK, index);
            String jmpKey = substitute(ctx, line.substring(7));
            if (ctx.jmp.containsKey(jmpKey)) {
                ctx.current = ctx.jmp.get(jmpKey);
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }
            return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Could not find jump destination");
        }

        // ends the tick regardless of remaining clock cycles
        if (lower.equals("endtick")) {
            return new ReturnInfo(EnumStatementReturn.END_TICK, index);
        }

        // requests unit to shut down
        if (lower.equals("shutdown")) {
            return new ReturnInfo(EnumStatementReturn.SHUTDOWN, index);
        }

        // loads the requested variable into the buffer
        if (lower.startsWith("load ")) {
            if (line.length() <= 5) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            ctx.buffer = ctx.variables.getString(line.substring(5));
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // saves the buffer with the specified name
        if (lower.startsWith("save ")) {
            if (line.length() <= 5) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Cannot save variable, buffer is empty");
            ctx.variables.setString(line.substring(5), ctx.buffer);
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // stores the specified value in the buffer
        if (lower.startsWith("buffer ")) {
            if (line.length() <= 7) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            ctx.buffer = line.substring(7);
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // runs the calculation, allows string substitution, saves result to buffer
        if (lower.startsWith("eval ")) {
            if (line.length() <= 5) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            String statement = substitute(ctx, line.substring(5));
            try {
                double result = Calculator.evaluateExpression(statement);
                ctx.buffer = "" + result;
            } catch(Throwable _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Not a valid expression"); }
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // runs the calculation, allows string substitution, rounds, saves result to buffer,
        if(lower.startsWith("evalr ")) {
            if(line.length() <= 6) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            String statement = substitute(ctx, line.substring(6));
            try {
                double result = Calculator.evaluateExpression(statement);
                ctx.buffer = "" + (int) Math.round(result);
            } catch(Throwable _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Not a valid expression"); }
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // runs the calculation from the buffer, allows string substitution, saves result to buffer
        switch (lower) {
            case "eval" -> {
                if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
                String statement = substitute(ctx, ctx.buffer);
                try {
                    double result = Calculator.evaluateExpression(statement);
                    ctx.buffer = "" + result;
                } catch (Throwable _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid expression"); }
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }

            case "evalr" -> {
                if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
                String statement = substitute(ctx, ctx.buffer);
                try {
                    double result = Calculator.evaluateExpression(statement);
                    ctx.buffer = "" + (int) Math.round(result);
                } catch (Throwable _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid expression"); }
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }


            // rounds the buffer down to the nearest integer
            case "rounddown", "floor" -> {
                if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
                try {
                    double d = Double.parseDouble(ctx.buffer);
                    ctx.buffer = "" + (int) Math.floor(d);
                } catch (Exception _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }


            // rounds the buffer up to the nearest integer
            case "roundup", "ceil" -> {
                if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
                try {
                    double d = Double.parseDouble(ctx.buffer);
                    ctx.buffer = "" + (int) Math.ceil(d);
                } catch (Exception _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }


            // rounds the buffer to the nearest integer (.5 cutoff rule)
            case "round", "nearest" -> {
                if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
                try {
                    double d = Double.parseDouble(ctx.buffer);
                    ctx.buffer = "" + (int) Math.round(d);
                } catch (Exception _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
                return new ReturnInfo(EnumStatementReturn.OK, index);
            }
        }

        // concatenate, same as buffer but evaluates $var$ substitutions
        if (lower.startsWith("concat ")) {
            if (line.length() <= 7) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Cannot concat, buffer is empty");
            ctx.buffer = substitute(ctx, line.substring(5));
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // compares the buffer with a value, allows substitutions
        if (lower.startsWith("eq ")) {
            if (line.length() <= 3) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
            ctx.buffer = ctx.buffer.equals(substitute(ctx, line.substring(3))) ? "true" : "false";
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // greater than buffer
        if (lower.startsWith("gtb ")) {
            if (line.length() <= 4) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
            try {
                double buffer = Double.parseDouble(ctx.buffer);
                double val =  Double.parseDouble(substitute(ctx, line.substring(3)));
                ctx.buffer = val > buffer ? "true" : "false";
            } catch (Exception _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // lower than buffer
        if (lower.startsWith("ltb ")) {
            if (line.length() <= 4) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
            try {
                double buffer = Double.parseDouble(ctx.buffer);
                double val =  Double.parseDouble(substitute(ctx, line.substring(3)));
                ctx.buffer = val < buffer ? "true" : "false";
            } catch(Exception _) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // greater than or equal buffer
        if (lower.startsWith("geb ")) {
            if (line.length() <= 4) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
            try {
                double buffer = Double.parseDouble(ctx.buffer);
                double val =  Double.parseDouble(substitute(ctx, line.substring(3)));
                ctx.buffer = val >= buffer ? "true" : "false";
            } catch(Exception ex) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // lower than or equal buffer
        if (lower.startsWith("leb ")) {
            if (line.length() <= 4) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer is empty");
            try {
                double buffer = Double.parseDouble(ctx.buffer);
                double val =  Double.parseDouble(substitute(ctx, line.substring(3)));
                ctx.buffer = val <= buffer ? "true" : "false";
            } catch(Exception ex) { return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Buffer does not have a valid number"); }
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // sends an RoR signal using the buffer's contents as the message over the supplied channel
        if (lower.startsWith("send ")) {
            if (line.length() <= 5) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Cannot send, buffer is empty");
            RTTYSystem.broadcast(ctx.world, substitute(ctx, line.substring(5)), ctx.buffer);
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        // listens to an RoR signal using the supplied channel name and saves it to the buffer
        if (lower.startsWith("listen ")) {
            if (line.length() <= 7) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Expected an argument");
            if (ctx.buffer.isEmpty()) return new ReturnInfo(EnumStatementReturn.PARAMETER_ERROR, index, "Cannot listen, Buffer is empty");
            RTTYSystem.RTTYChannel chan = RTTYSystem.listen(ctx.world, substitute(ctx, line.substring(7)));
            if (chan != null) ctx.buffer = chan.signal + "";
            return new ReturnInfo(EnumStatementReturn.OK, index);
        }

        return new ReturnInfo(EnumStatementReturn.UNRECOGNIZED_COMMAND, index);
    }

    public String substitute(ParseContext ctx, String statement) {
        if (!statement.contains("$")) return statement;

        String[] frags = statement.split("\\$");
        if (frags.length % 2 == 0 || frags.length < 3) return statement;

        // since var names are enclosed with $ signs, we assume that every evenly numbered fragment is a var name
        // example: 5 + $val1$ * $val2$ / (-$val3$)
        // equals "5 + ", "val1", "* ", "val2", "/ (-", "val3", ")"
        //         1       2       3     4       5       6       7
        for(int i = 1; i < frags.length; i += 2) {
            // special case, if we try to substitute $buffer$ then read the literal buffer
            if (frags[i].equals("buffer")) frags[i] = ctx.buffer;
            else frags[i] = ctx.variables.getString(frags[i]);
        }

        return String.join("", frags);
    }

    @Override
    public void generateJumpPoints(ParseContext ctx, String line, int index) {
        if (line.startsWith("dest ") && line.length() > 5) {
            ctx.jmp.put(line.substring(5), index);
        }
    }
}
