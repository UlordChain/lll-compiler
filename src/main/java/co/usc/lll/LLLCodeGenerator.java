
/****************************************************************************************************************
 *  Supported constructs:
 *
 *  Literals
 *     (lit POS STRING) Places the string STRING in memory at POS and evaluates to its length.
 *     string      "string"
 *     number     (e.g. 1234)
 *     hex-number (e.g. 0x1234)
 *  Flow control
 *    (for INIT PRED POST BODY) evaluates INIT once (ignoring any result), then evaluates BODY and POST (discarding the result of both) as long as PRED is true.
 *    (while COND BODY)
 *    (until BODY COND)
 *
 *  The following constructs are not yet supported:
 *  Literals
        Ethereum/Bitcoin denominations. e.g. 69, 0x42, 10 ether/btc or 3finney.

        When literals must be included that can be placed into memory, there is the lit operation:

        (lit POS INT1 INT2 ...) Places each of the integers INT1, INT2 &c. in to memory beginning at POS and each 32-bytes apart. Evaluates to total memory used.
        (lit POS BIGINT) Places BIGINT into memory at POS and evaluates to the number of bytes it takes. Unlike for the previous case, BIGINT may be arbitrarily large, and thus if specified as hex, can facilitate storing arbitrary binary data.

 * Macros
        Macro overloading
 * Code
        For handling cases where code needs to be compiled and passed around, there is the lll operation:

        (lll EXPR POS MAXSIZE)
        (lll EXPR POS)
  * Misc
     (send <gaslimit> <to> <value>): Sends <value> Wei to the account <to> with a limit on the allowed gas <gaslimit>.
     (msg <to> <data>): Sends a message to the account <to> with word-sized data item <data>, evaluates to the word-sized return value.
     (msg <to> <value> <data>): Sends a message to the account <to> with word-sized data item <data> and <value> Wei, evaluates to the word-sized return value.
     (msg <gaslimit> <to> <value> <data>): Sends a message to the account <to> with word-sized data item <data> and <value> Wei and a limit of gas <gaslimit>, evaluates to the word-sized return value.
     (msg <gaslimit> <to> <value> <data> <datasize>): Sends a message to the account <to> with data of length <datasize>, stored in memory at position <data> and <value> Wei and a limit of gas <gaslimit>, evaluates to the word-sized return value.
     (create <code>): Creates a new contract with initialisation code <code>.
     (create <endowment> <code>): Creates a new contract with endowment Wei and initialisation code <code>.
     (sha3 <value>): Evaluates to the sha3 of the given <value>.
     (sha3pair <value1> <value2>): Evaluates to the sha3 of the 64-bytes given by the concatenation of <value1> and <value2>.
     (sha3trip <value1> <value2> <value3>): Evaluates to the sha3 of the 64-bytes given by the concatenation of <value1>, <value2> and <value3>.
     (return <value>): Returns from the call with the word-sized data <value>.
     (returnlll <expression>): Returns from the call with the code representing the LLL expression <expression>.
      allgas: All gas available, when used as a parameter to send, call or msg.

 * The following constructs will never be implemented:
    (raw EXPR1 EXPR2 ...) -> completely insecure
    (ref var) returns a reference to the memory where var is assigned to.
        This seems completely pointless since any var it iself a memory address, never refers to the value
        contained unless it is prefixed by @ or @@.
*****************************************************************************************************************/
package co.usc.lll;

import co.usc.lll.asm.CodeBlock;
import co.usc.lll.asm.EVMAssemblerHelper;
import co.usc.lll.asm.OpCode;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

public class LLLCodeGenerator {

    private class Replacement {
        boolean isVar;

        //for variable replacements
        String var;
        int depth;
        // for macro replacements
        Object repNode;


        public Replacement(String avar, int adepth) {
            var = avar;
            depth = adepth;
            isVar = true;
        }

        public Replacement(String avar, Object r,int scope) {
            var = avar;
            depth= scope;
            isVar = false;
            repNode = r;
        }
    }

    EVMAssemblerHelper helper = new EVMAssemblerHelper();
    Stack<Replacement> replacements = new Stack<>();

    int stackLevel = 0;

    public EVMAssemblerHelper getHelper() {
        return helper;
    }

    public void error(String s, LLLNode node) throws LLLCompilationError {
        throw new LLLCompilationError(s + " at line " + node.line);
    }

    public static String extractHex(String str) {
        if ((str.length() >= 2) && (str.charAt(0) == '0') && (str.charAt(1) == 'x')) {
            return str.substring(2);
        }
        return null;
    }


    public BigInteger parseValue(String tok) {
        BigInteger num;
        String aHex = extractHex(tok);
        if (aHex != null)
            num = new

                    BigInteger(aHex, 16);

        else
            num = new

                    BigInteger(tok, 10);

        return num;
    }

    //public boolean isAlphaNumVar(String name) {
    //    return name.matches("[a-zA-Z]+[0-9]*");
    //}
    public boolean isAlphaNumVar(String name) {
        char[] chars = name.toCharArray();
        boolean first = true;
        for (char c : chars) {

            if ((!Character.isAlphabetic(c) && (!Character.isDigit(c)) && c != '_' && c != '$')) {
                return false;
            }
            if ((first) && (Character.isDigit(c)))
                return false;
            first = false;
        }

        return true;
    }

    Map<String, byte[]> vars = new HashMap<>();
    int memoryUsed = 0;

    int currentReplacementScope = -10;

    public int findReplacement(String lit) {
        // local variable, look for replacement
        int found = -1;
        int start;
        if (currentReplacementScope!=-10)
            start = currentReplacementScope-1;
            else
            start = replacements.size() - 1;

        for (int i = start; i >= 0; i--) {
            if (replacements.get(i).var.equalsIgnoreCase(lit)) {
                found = i;
                break;
            }
        }
        return found;
    }

    public CodeBlock getCodeForReplacement(LLLNode node, int found) throws LLLCompilationError {
        Replacement r = replacements.get(found);
        if (r.isVar)
            return getCodeForVarReplacement(node, r);
        else
            return getCodeForMacroReplacement(node, r);
    }

    public CodeBlock getCodeForMacroReplacement(LLLNode node, Replacement r) throws LLLCompilationError {
        CodeBlock block;
        int oldScope =currentReplacementScope;
        currentReplacementScope =r.depth;
        block = getCodeFor(node,r.repNode);
        currentReplacementScope = oldScope;
        return block;

    }

    public CodeBlock getCodeForVarReplacement(LLLNode node, Replacement r ) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(null);

        // now generate code to access at depth
        int difDepth = stackLevel - r.depth;
        if ((difDepth >= 16) || (difDepth <= 0))
            error("Local variable access to deep " + difDepth, node);
        int opCode = OpCode.DUP1.opcode + difDepth - 1;
        block.startWrite();
        block.writer().write(opCode);
        block.endWrite();
        return block;
    }

    public void warn(String s) {
        System.out.println(s);
    }


    public CodeBlock getCodeLiteral(LLLNode node, String lit) throws LLLCompilationError {

        // check if it is a number

        byte[] value = null;
        try {
            value = parseValue(lit).toByteArray();
        } catch (NumberFormatException e) {
            lit = lit.toUpperCase();
            int found = findReplacement(lit);

            if ((found < 0) && (lit.startsWith("$")))
                error("Local var ($) not found: " + lit, node);
            if (found >= 0)
                return getCodeForReplacement(node, found);
            else {
                Macro m = macros.get(lit);
                if ((m != null) && (m.args == null)) // a contant macro
                    return codeMacroExpansion(node, m,false);

                // Is it a variable name?

                if (isAlphaNumVar(lit)) {
                    // define or take reference
                    if (vars.containsKey(lit)) {
                        value = vars.get(lit);
                    } else {
                        // add
                        warn("Memory variable " + lit + " auto-created");
                        value = BigInteger.valueOf(memoryUsed).toByteArray();
                        vars.put(lit, value);
                        memoryUsed++;
                    }
                } else
                    error("invalid variable name: " + lit, node);
            }
        }

        CodeBlock block = new CodeBlock(null); // the SourceRef for a literal is missing. Don't print the whole exp.
        block.startWrite();
        codePUSHValue(node, block,value);
        block.endWrite();
        return block;
    }

    public void codePUSHValue(LLLNode node,CodeBlock block,byte[] value) throws LLLCompilationError {

        if (value.length > 32) {
            error("Value too long", node);
        }
        int pushOpcode = value.length + OpCode.PUSH1.val() - 1;

        try {
            block.writer().write(pushOpcode);
            block.writer().write(value);
        } catch (IOException e) {
        }
    }

    public CodeBlock getCodeFor(LLLNode node, Object o) throws LLLCompilationError {
        if (o instanceof LLLNode)
            return generateInnerCodeBlock((LLLNode) o);
        if (o instanceof String)
            return getCodeLiteral(node, (String) o);
        throw new LLLCompilationError("Invalid token type");
    }


    public byte[] generate(LLLNode node) throws LLLCompilationError {
        return generateCodeBlock(node).getCode();
    }

    public Function getFuncAt(int id) {
        for (int i = 0; i < functions.size(); i++) {
            Function f = functions.get(i);
            if (f.labelID == id)
                return f;
        }
        return null;
    }

    public CodeBlock generateCodeBlock(LLLNode node) throws LLLCompilationError {
        CodeBlock c = generateInnerCodeBlock(node);
        if (c == null)
            return new CodeBlock(null);

        Set<Integer> used = new HashSet<>();
        Set<Integer> unew = new HashSet<>();
        /*
        for (int i=0;i<functions.size();i++) {
            allf.put(functions.get(i).labelID,functions.get(i));
        }
        */
        /*
        // New we must insert all used functions
        for (int i=0;i<functions.size();i++) {
            Function f = functions.get(i);
            //use-counting must be recursive, because a function can use another
            if (f.useCount > 0) {
                 c.append(helper, f.code);;
            }
        }
        */

        unew.addAll(c.getCalledFuncs());

        while (!unew.isEmpty()) {
            Set<Integer> next = new HashSet<>();
            for (Integer i : unew)
                if (!used.contains(i)) {
                    used.add(i);
                    next.addAll(getFuncAt(i).code.getCalledFuncs());
                }
            unew = next;
        }

        for (int i = 0; i < functions.size(); i++) {
            Function f = functions.get(i);
            //use-counting must be recursive, because a function can use another
            if (used.contains(f.labelID))
                c.append(helper, f.code);
        }

        if (!helper.performFixUp(c))
            error("Cannot find referenced labels", node);

        return c;
    }

    public CodeBlock codeAND_OR(LLLNode node, String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);
        boolean isAND = (opcode.equals("&&"));
        int trueVal = 0;
        if (isAND) trueVal = 1;
        int falseLabel = helper.getNewLabel(null);
        int exitLabel = helper.getNewLabel(null);
        for (int i = 1; i < node.argCount(); i++) {
            block.append(helper, getCodeFor(node, node.argAt(i)));
            block.startWrite();
            if (isAND)
                block.writer().write(OpCode.ISZERO.opcode);
            block.writePushTag(falseLabel);
            block.writer().write(OpCode.JUMPI.opcode);
            block.endWrite();
        }
        block.startWrite();
        // TRUE (for AND), FALSE (for OR)

        block.writePushByte(trueVal);
        block.writePushTag(exitLabel);
        block.writer().write(OpCode.JUMP.opcode);
        // FALSE
        helper.setLabelPosition(falseLabel, block, block.writeOffset());
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.writePushByte(1 - trueVal);
        // EXIT
        helper.setLabelPosition(exitLabel, block, block.writeOffset());
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();
        return block;
    }

    public CodeBlock codeIF(LLLNode node, String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);

        int falseLabel = helper.getNewLabel(null);
        int exitLabel = helper.getNewLabel(null);
        if (node.argCount() > 3) // cond if else
            error("too many arguments for IF", node);

        // Condition
        block.append(helper, getCodeFor(node, node.argAt(0)));
        block.startWrite();
        block.writer().write(OpCode.ISZERO.opcode);
        block.writePushTag(falseLabel);
        block.writer().write(OpCode.JUMPI.opcode);
        block.endWrite();

        CodeBlock trueBlock = getCodeFor(node, node.argAt(1));
        block.append(helper, trueBlock);



        if (!trueBlock.reverts) {
            block.startWrite();
            block.writePushTag(exitLabel);
            block.writer().write(OpCode.JUMP.opcode);
            block.endWrite();
        }

        // FALSE
        helper.setLabelPosition(falseLabel, block, block.writeOffset());
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();

        if (node.argCount() >= 3)
            block.append(helper, getCodeFor(node, node.argAt(2)));

        else {
            // push false, because IF must always push something
            block.startWrite();
            block.writePushByte(0);
            block.endWrite();
        }

        if (!trueBlock.reverts) {
            // EXIT
            helper.setLabelPosition(exitLabel, block, block.writeOffset());
            block.startWrite();
            block.writer().write(OpCode.JUMPDEST.opcode);
            block.endWrite();
        }
        return block;
    }
    //(for INIT PRED POST BODY) evaluates INIT once (ignoring any result), then evaluates BODY and POST (discarding the result of both) as long as PRED is true.

    public CodeBlock codeFOR(LLLNode node, String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);

        int condLabel = helper.getNewLabel(null);
        int exitLabel = helper.getNewLabel(null);
        if (node.argCount() != 3) // cond if else
            error("invalid number of arguments for FOR", node);

        // initialization
        block.append(helper, getCodeFor(node, node.argAt(0)));
        block.startWrite();
        block.writer().write(OpCode.POP.opcode); // dispose initialization result
        block.endWrite();
        helper.setLabelPosition(condLabel, block, block.writeOffset());

        // condition
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();

        block.append(helper, getCodeFor(node, node.argAt(1)));
        block.startWrite();
        // if condition is false, jump to exit
        block.writer().write(OpCode.ISZERO.opcode);
        block.writePushTag(exitLabel);
        block.writer().write(OpCode.JUMPI.opcode);
        block.endWrite();

        // execute post
        block.append(helper, getCodeFor(node, node.argAt(2)));

        // jump to conditon
        block.startWrite();
        block.writePushTag(condLabel);
        block.writer().write(OpCode.JUMP.opcode);
        block.endWrite();

        // EXIT
        helper.setLabelPosition(exitLabel, block, block.writeOffset());
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();
        return block;
    }


    // (WHILE cond code)
    public CodeBlock codeWHILE(LLLNode node, String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);

        int condLabel = helper.getNewLabel(null);
        int exitLabel = helper.getNewLabel(null);
        if (node.argCount() != 2) // cond if else
            error("invalid number of arguments for WHILE", node);

        helper.setLabelPosition(condLabel, block, block.writeOffset());

        // condition
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();

        block.append(helper, getCodeFor(node, node.argAt(0)));
        block.startWrite();

        // if condition is false, jump to exit
        block.writer().write(OpCode.ISZERO.opcode);
        block.writePushTag(exitLabel);
        block.writer().write(OpCode.JUMPI.opcode);
        block.endWrite();

        // execute code
        CodeBlock body = getCodeFor(node, node.argAt(1));

        if (body!=null)
            block.append(helper, body);

        // jump to conditon
        block.startWrite();

        if (body!=null)
            // POP Last expression return value
            block.writer().write(OpCode.POP.opcode);

        block.writePushTag(condLabel);
        block.writer().write(OpCode.JUMP.opcode);
        block.endWrite();

        // EXIT
        helper.setLabelPosition(exitLabel, block, block.writeOffset());
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();
        return block;
    }


    // (UNTIL body cond)
    public CodeBlock codeUNTIL(LLLNode node, String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);

        int startLabel = helper.getNewLabel(null);
        if (node.argCount() != 2) // cond if else
            error("invalid number of arguments for UTIL", node);

        helper.setLabelPosition(startLabel, block, block.writeOffset());

        // condition
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();

        // execute code
        CodeBlock body =getCodeFor(node, node.argAt(0));
        block.append(helper, body);

        if (body!=null) {
            // Pop return argument of code executed
            block.startWrite();
            block.writer().write(OpCode.POP.opcode);
            block.endWrite();
        }
        // compile conditon
        block.append(helper, getCodeFor(node, node.argAt(1)));

        block.startWrite();
        block.writePushTag(startLabel);
        block.writer().write(OpCode.JUMPI.opcode);
        block.endWrite();

        return block;
    }

    public CodeBlock codeSEQ(LLLNode node, String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(null); // this mat be too long... do not copy the whole seq

        for (int i = 0; i < node.argCount(); i++) {
            CodeBlock c = getCodeFor(node, node.argAt(i));
            block.append(helper, c);
            // thow array all but last stack value
            if ((c != null) && (i != node.argCount() - 1)) {
                block.startWrite();
                block.writer().write(OpCode.POP.opcode); // remove local var
                block.endWrite();
            }

        }
        return block;
    }

    public void writeAsmIns(LLLNode node, CodeBlock block, String tok)throws LLLCompilationError  {
        try {
            byte[] value = parseValue(tok).toByteArray();
            if (value.length > 32) {
                error("constant to long", node);
            }
            int pushOpcode = value.length + OpCode.PUSH1.val() - 1;
            block.writer().write(pushOpcode);
            block.writer().write(value);
        } catch (NumberFormatException e)
        {
            byte opcode = OpCode.byteVal(tok);
            block.writer().write(opcode);
        } catch (IOException e) {
            error("IOException",node);
        }
    }


    public CodeBlock codeREVERT(LLLNode node) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(null); // this mat be too long... do not copy the whole seq
        block.startWrite();
        //
        block.writePushInt(Integer.MAX_VALUE); //will generate an exception
        block.writer().write(OpCode.JUMP.opcode);
        block.endWrite();
        block.reverts = true;
        return block;
    }

    public CodeBlock codeSEND(LLLNode node) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(null); // this mat be too long... do not copy the whole seq

        block.startWrite();
        //
        block.writePushByte(0); //outSize
        block.writePushByte(0); //outOfs
        block.writePushByte(0); //inSize
        block.writePushByte(0); //inOfs
        block.endWrite();

        block.append(helper, getCodeFor(node, node.argAt(1))); // value
        block.append(helper, getCodeFor(node, node.argAt(0))); // address

        block.startWrite();
        block.writePushInt(2300); // gas for SEND
        block.writer().write(OpCode.CALL.opcode);
        block.endWrite();
        // returns 1 on success / 0 on failure
        return block;
    }

    private class Macro {
        List args;
        Object code; // can be a constant also

        public Macro(List args,Object code) {
            this.args = args;
            this.code = code;
        }
    }

    public CodeBlock codeLOCAL(LLLNode node) throws LLLCompilationError {
        // makes all macros and functions defined inside to be local
        // I wonder if this shouldn't be the default. I must test lllc.
        return null;
    }

    // lit is actually (MSTORE var literal) .... but returns the length.
    public CodeBlock codeLIT(LLLNode node) throws LLLCompilationError {
        if (node.argCount() != 2)
            error("LIT requires 3 arguments", node);
        CodeBlock block = new CodeBlock(node.ref);
        CodeBlock addr = getCodeFor(node,node.argAt(0));
        block.append(helper,addr);
        if (!(node.argAt(1) instanceof String))
            error("Literal string expected",node);
        String lit = (String) node.argAt(1);
        if (!lit.startsWith("\""))
            error("String expected",node);
        lit = lit.substring(1,lit.length()-1);
        if (lit.length()>32)
            error("Literal string too long",node);

        BigInteger bi = new BigInteger("0");

        // Convert string made of ANSI chars to uint256
        for (int i =0;i<lit.length();i++) {
            BigInteger charAsBi = BigInteger.valueOf(lit.charAt(i));
            bi = bi.shiftLeft(8).add(charAsBi);
        }
        byte[] value = bi.toByteArray();
        block.startWrite();
        codePUSHValue(node, block,value);
        block.writer().write(OpCode.MSTORE.opcode);
        block.writePushByte(lit.length());
        block.endWrite();
        return block;
    }

    public CodeBlock codeVARDEPTH(LLLNode node) throws LLLCompilationError {
        if (node.argCount()!=1)
            error("REF requires only one argument",node);
        if (!(node.argAt(0) instanceof String))
            error("REF requires an variable as argument",node);

        String lit = (String) node.argAt(0);

        lit = lit.toUpperCase();
        int found = findReplacement(lit);
        if (found < 0)
            error("Var not found: " + lit, node);

        CodeBlock block = new CodeBlock(null);
        Replacement r = replacements.get(found);
        if (!r.isVar)
            error("Macro given when var expected: " + lit, node);

        // now generate code to access at depth
        int difDepth = stackLevel - r.depth;
        block.startWrite();
        byte[] value = BigInteger.valueOf(difDepth).toByteArray();
        codePUSHValue(node,block,value);
        block.endWrite();
        return block;
    }

    Map<String,Macro> macros = new HashMap<>();

    public CodeBlock codeDEF(LLLNode node) throws LLLCompilationError {
        if (currentReplacementScope!=-10)
            error("Cannot define a macro in a macro argument",node);

        if (!(node.argAt(0) instanceof String))
            error("First macro argument must be a name",node);

        String name = (String) node.argAt(0);
        if (!name.startsWith("'"))
            error("Macro name must start with the ' character ",node);

        name = name.substring(1).toUpperCase();

        if (node.argCount()==2) {// contant, no arguments
            Object valueObj = node.argAt(1);
            macros.put(name, new Macro(null, valueObj));
        }
        else {// first arguments is list of parameters (can be empty)
            Object argListObj = node.argAt(1);
            if (!(argListObj instanceof LLLNode))
                error("Expecting argument list", node);

            List args = ((LLLNode) argListObj).elements;

            // Now check that the argument list consist solely on $ identifiers
            // also create list of identifiers
            checkArgumentIdentifiers((LLLNode) argListObj, args, false);
            macros.put(name, new Macro(args, node.argAt(2)));
        }
        return null;

    }

    public CodeBlock codeASM(LLLNode node) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(null); // this mat be too long... do not copy the whole seq

        block.startWrite();
        for (int i = 0; i < node.argCount(); i++) {
            if (!(node.argAt(i) instanceof String))
                error("Asm instruction expected",node);

            String ins = (String) node.argAt(i);
            writeAsmIns(node,block,ins);
        }
        block.endWrite();
        return block;
    }

    public void checkArgumentIdentifiers(LLLNode node, List args,boolean startWithDS) throws LLLCompilationError {
        List<String> list = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            if (!(args.get(i) instanceof String))
                error("Expecing an argument identifier", node);
            String id = (String) args.get(i);
            if ((startWithDS) && (!id.startsWith("$")))
                error("Argument identifiers must start with symbol $", node);
            if ((!startWithDS) && (!isAlphaNumVar(id)))
               error("Argument identifier must be alphanumeric", node);

            if (list.indexOf(id)>=0)
                error("Duplicate argument id", node);
            list.add(id);

        }
    }

    private class Function {
        CodeBlock code;
        List args;
        String name;
        int labelID;
        int useCount;

        public Function(String aname,List argList,CodeBlock funCode,int alabelID) {
            name = aname;
            args = argList;
            code = funCode;
            labelID = alabelID;
        }

    }

    List<Function> functions = new ArrayList<>();


    public CodeBlock codeFUNC(LLLNode node,String opcode) throws LLLCompilationError {

        if (node.argCount()!=3)
            error("Function definition requires three arguments",node);

        // First argument is function name
        Object objName=node.argAt(0);
        if (!(objName instanceof String))
            error("Expecting function name",node);

        String name = (String) objName;
        name = name.toUpperCase();

        // first arguments is list of parameters (can be empty)
        Object argListObj =node.argAt(1);
        if (!(argListObj instanceof LLLNode))
            error("Expecting argument list",node);

        List args = ((LLLNode) argListObj).elements;

        // Now check that the argument list consist solely on $ identifiers
        // also create list of identifiers
        checkArgumentIdentifiers((LLLNode) argListObj,args,false);

        // pushed from start to end, like C (unlike Pascal)
        for (int i = 0; i < args.size(); i++) {
            replacements.add(new Replacement((String) args.get(i), stackLevel));
            stackLevel++;
        }
        CodeBlock block = new CodeBlock(node.ref);
        int enterLabel = helper.getNewLabel(name);

        helper.setLabelPosition(enterLabel,block,block.writeOffset());
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();

        CodeBlock funCode = getCodeFor(node,node.argAt(2));
        block.append(helper,funCode);
        block.startWrite();

        // Caller MUST make room for return value always
        // Now in stack is return value. Must exchange with caller provided dummy value
        int swapOp = OpCode.SWAP1.opcode +args.size();
        if (swapOp>OpCode.SWAP16.opcode)
            error("Too many arguments",node);

        block.writer().write(swapOp);

        // now remove the dummy from top
        block.writer().write(OpCode.POP.opcode);

        for (int i = 0; i < args.size(); i++) {
            block.writer().write(OpCode.POP.opcode);
            replacements.pop();
            stackLevel--;
        }
        block.writer().write(OpCode.JUMPI.opcode);
        block.endWrite();

        // store function
        functions.add(new Function(name,args,block,enterLabel));
        // return a non-code
        return null;
    }

    public CodeBlock codeExecFunc(LLLNode node,int fIndex) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);
        Function func = functions.get(fIndex);
        if (node.argCount()!=func.args.size())
            error("Function requires different number of arguments ("+func.args.size()+")",node);



        block.startWrite();
        // First push a dummy return value (currenlty only a single value can be returned)
        block.writePushByte(0);

        // Then push return address
        int returnAddressLabel = helper.getNewLabel(null);
        block.writePushTag(returnAddressLabel);
        block.endWrite();

        // push arguments
        for (int i = 0; i < node.argCount(); i++) {
            block.append(helper,getCodeFor(node,node.argAt(i)));

        }
        // internal function call (does not use the CALL opcode),
        // this is done by pushing the return address and then the function to be called

        func.useCount++;

        block.startWrite();
        block.addCalledFunc(func.labelID);
        block.writePushTag(func.labelID);
        block.writer().write(OpCode.JUMPI.opcode);
        // Now in stack is return value
        helper.setLabelPosition(returnAddressLabel,block,block.writeOffset());
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();
        return block;
    }


    public CodeBlock codeWHEN_UNLESS(LLLNode node,String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);
        int falseLabel = helper.getNewLabel(null);
        int exitLabel = helper.getNewLabel(null);
        if (node.argCount()>2) // cond if else
            error("too many arguments for "+opcode,node);

        // Condition
        block.append(helper,getCodeFor(node,node.argAt(0)));
        block.startWrite();
        if (opcode.equals("WHEN"))
            block.writer().write(OpCode.ISZERO.opcode);
        block.writePushTag(falseLabel);
        block.writer().write(OpCode.JUMPI.opcode);
        block.endWrite();

        // True for WHEN / False for UNLESS
        CodeBlock eblock = getCodeFor(node,node.argAt(1));
        block.append(helper,eblock);
        if (!eblock.reverts) {
            block.startWrite();
            block.writePushTag(exitLabel);
            block.writer().write(OpCode.JUMP.opcode);
            block.endWrite();
        }
        // FALSE
        helper.setLabelPosition(falseLabel,block,block.writeOffset());
        block.startWrite();
        block.writer().write(OpCode.JUMPDEST.opcode);
        block.endWrite();
        // push false, because IF must always push something
        block.startWrite();
        block.writePushByte(0);
        block.endWrite();
        if (!eblock.reverts) {
            // EXIT
            helper.setLabelPosition(exitLabel, block, block.writeOffset());
            block.startWrite();
            block.writer().write(OpCode.JUMPDEST.opcode);
            block.endWrite();
        }
        return block;
    }


    public CodeBlock codeWITH(LLLNode node,String opcode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);

        if (node.argCount() != 3) // id value exp
            error("too many arguments for WITH", node);

        // We allow with to specify a single var or a list of vars
        if (node.argAt(0) instanceof LLLNode) {
            LLLNode argNode = (LLLNode) node.argAt(0);
            List args = argNode.elements;
            if (argNode.elements.size()<1)
                error("At least one arg required",node);
            for (Object a :args) {
                if (a instanceof LLLNode)
                    error("local variable expected in WITH: ", argNode);
                String var = (String) a;
                if (!var.startsWith("$"))
                    error("Local variable expected in with at " + var, argNode);
            }
            if (!(node.argAt(1) instanceof LLLNode))
                error("Expecting value list ", node);

            LLLNode valNode = (LLLNode) node.argAt(1);
            // the size must match
            if (valNode.elements.size()!=argNode.elements.size())
                error("Arg and elements size mismatch",node);

            for (Object a :valNode.elements) {
                // Evaluate vars
                block.append(helper, getCodeFor(node, a));
            }

            for (Object a :args) {
                // build replacement code
                replacements.add(new Replacement((String) a, stackLevel));
                stackLevel++;
            }
            // True: return expression
            block.append(helper, getCodeFor(node, node.argAt(2)));
            block.startWrite();
            // pop the var from the stack, but leave the previous value
            // The easiest way is swapping with the first element
            // and them poping rest
            int swapOp = OpCode.SWAP1.opcode +args.size()-1;
            if (swapOp>OpCode.SWAP16.opcode)
                error("Too many arguments ",node);
            block.writer().write(swapOp);
            for (Object a :args) {
                block.writer().write(OpCode.POP.opcode); // remove local var
                replacements.pop();
                stackLevel--;
            }
            block.endWrite();
        } else {
            String var = (String) node.argAt(0);
            if (!var.startsWith("$"))
                error("Local variable expected in with ", node);

            // Evaluate var
            block.append(helper, getCodeFor(node, node.argAt(1)));

            // build replacement code
            replacements.add(new Replacement(var, stackLevel));
            stackLevel++;

            // True: return expression
            block.append(helper, getCodeFor(node, node.argAt(2)));
            block.startWrite();
            // pop the var from the stack, but leave the previous value
            // The easiest way is swapping them before popping
            block.writer().write(OpCode.SWAP1.opcode);
            block.writer().write(OpCode.POP.opcode); // remove local var
            block.endWrite();
            replacements.pop();
            stackLevel--;
        }
        return block;
    }

    private class AliasReplacement {
        String opcode;
        OpCode addCode;

        public AliasReplacement(String opcode,OpCode addCode) {
            this.opcode =opcode;
            this.addCode = addCode;
        }
    }

    AliasReplacement getAlias(String opcode) {
        if (opcode.equals(">=")) {
            return new AliasReplacement("LT", OpCode.ISZERO);
        } else if (opcode.equals("<=")) {
            return new AliasReplacement("GT", OpCode.ISZERO);
        } else if (opcode.equals("!=")) {
            return new AliasReplacement("EQ", OpCode.ISZERO);
        }
        return null;
    }

    public boolean isOpcode(String opcode) {
        return (OpCode.contains(opcode));
    }

    public CodeBlock codeOpcode(LLLNode node,String opcode,OpCode addCode) throws LLLCompilationError {
        CodeBlock block = new CodeBlock(node.ref);

        byte opcodeByte = OpCode.byteVal(opcode);
        OpCode opcodeDesc = OpCode.code(opcodeByte);

        // LLL allows accumulating several operations
        // So (ADD a b c) is permitted, but only for binary operations
        if (opcodeDesc.require() > node.argCount())
            error("To few operands for: " + opcode, node);

        if ((opcodeDesc.require()!=2) && (opcodeDesc.require() !=node.argCount()))
            error("invalid number of arguments for: " + opcode, node);

        if (opcodeDesc.ret()>1)
            error("Only supported opcodes that return 0 or 1 value",node);


        int operations = node.argCount() - opcodeDesc.require()  +1;
        // Note that operations are pushed in the opposite of orden of appearaance in text
        // (GT A B) -> Push A, Push B, GT
        //
        // This is because aithhmetic opcodes work like that. E.g. to do 3 - 1 the operations are
        // PUSH 1, PUSH 3, SUB

        // When accumulating non-commutative operations, this also works as expected:
        // E.g. (SUB 8 2 2) = PUSH 2, PUSH 2, PUSH 8  = ((8-2)-2)

        for (int i = node.argCount()-1; i >=0; i--) {
            block.append(helper, getCodeFor(node, node.argAt(i)));
            stackLevel++;
        }
        block.startWrite();
        for(int i=0;i<operations;i++) {
            block.writer().write(opcodeByte);
            if (addCode != null)
                block.writer().write(addCode.opcode);
        }
        // The following opcodes do not push anything on the stack. The language requires that
        // every expression pushes a value, so a dummy value must be pushed.
        // CALLDATACOPY CODECOPY EXTCODECOPY MSTORE
        // MSTORE8 SSTORE
        // LOG0..LOG4 HIBERNATE WAKEUP
        // ACCEPTVALUE NOP SEND SUICIDE
        // The following opcodes are forbitten
        if ((opcodeByte==OpCode.POP.opcode) ||
            (opcodeByte==OpCode.JUMP.opcode) ||
            (opcodeByte==OpCode.JUMPDEST.opcode) ||
                    (opcodeByte==OpCode.JUMPI.opcode))
            error("Forbithen opcode: "+opcode,node);

        if (opcodeDesc.ret()==0)
            if (opcodeByte!=OpCode.RETURN.opcode) // RETURN does nor need push
                block.writePushByte(1); // true

        block.endWrite();
        stackLevel -= node.argCount();
        return block;

    }

    public int getFuncIndex(String opcode) {
        for(int i =functions.size()-1;i>=0;i--) {
            if (functions.get(i).name.equals(opcode)) {
                return i;
            }
        }
        return -1;
    }


    public CodeBlock codeMacroExpansion(LLLNode node,Macro m,boolean openBrackets) throws LLLCompilationError{

        /* form MACRO(args) form:
        if (!(node.argAt(0) instanceof LLLNode))
            error("Expecting arguments for macro",node);

        List sentArgs = ((LLLNode) node.argAt(0)).elements;

        if (sentArgs.size()!=m.args.size())
            error("Macro requires different number of arguments ("+m.args.size()+")",node);
        *
        */
        CodeBlock block = null;
        if (m.args==null) { // contant
            if ((openBrackets) && (node.argCount()!=0))
                error("Constant macro expect no arguments",node);
            block = getCodeFor(node,m.code);
        } else {

            if (node.argCount() != m.args.size())
                error("Macro requires different number of arguments (" + m.args.size() + ")", node);

            int scope = replacements.size();
            for (int i = 0; i < m.args.size(); i++) {
                replacements.add(new Replacement((String) m.args.get(i), node.argAt(i), scope));
            }
            block = getCodeFor(node, m.code);
            for (int i = 0; i < m.args.size(); i++) {
                replacements.pop();
            }
        }
        return block;
    }

    public CodeBlock generateInnerCodeBlock(LLLNode node) throws LLLCompilationError {

        //block.startWrite();
        String opcode = node.opcode.toUpperCase();
        OpCode addCode = null;
        AliasReplacement  ar = getAlias(opcode);
        if (ar!=null) {
            opcode= ar.opcode;
            addCode = ar.addCode;
        }
        if (opcode.equals("FUNC"))
            return codeFUNC(node,opcode); else

        if (opcode.equals("SEQ"))
            return codeSEQ(node,opcode); else
        if ((opcode.equals("&&")) || (opcode.equals("||")))
            return codeAND_OR(node,opcode); else
        if ((opcode.equals("IF")))
            return codeIF(node,opcode); else
        if ((opcode.equals("FOR")))
            return codeFOR(node,opcode); else
        if ((opcode.equals("WHILE")))
            return codeWHILE(node,opcode); else
        if ((opcode.equals("UNTIL")))
            return codeUNTIL(node,opcode); else
        if ((opcode.equals("WHEN")) || (opcode.equals("UNLESS")))
            return codeWHEN_UNLESS(node,opcode) ; else
        if ((opcode.equals("WITH")))
            return codeWITH(node,opcode); else
        if ((opcode.equals("ASM")))
            return codeASM(node); else
        if ((opcode.equals("SEND")))  // this is in conflict with SEND opcode
            return codeSEND(node); else
        if ((opcode.equals("REVERT")))  // this is in conflict with SEND opcode
            return codeREVERT(node); else
        if ((opcode.equals("DEF")))  // this is in conflict with SEND opcode
            return codeDEF(node); else
        if ((opcode.equals("LOCAL")))  //
            return codeLOCAL(node); else
        if ((opcode.equals("VARDEPTH")))  //
            return codeVARDEPTH(node); else
        if ((opcode.equals("LIT")))  //
            return codeLIT(node);

        if (isOpcode(opcode))
            return codeOpcode(node,opcode,addCode);

        int f = getFuncIndex(opcode);
        if (f>=0)
            return codeExecFunc(node,f);

        Macro m = macros.get(opcode);
        if (m!=null)
            return codeMacroExpansion(node,m,true);

        error("unexpected opcode: " + opcode, node);
        return null;
    }
}
