package co.usc.lll;

import java.util.Stack;

public class LLLParser {

    public void error(String s) throws LLLCompilationError {
        throw new LLLCompilationError(s+" at line "+st.line);
    }

    public String opposite(String t) throws LLLCompilationError {
        if (t.equals("{")) return "}";
        if (t.equals("(")) return ")";
        if (t.equals("[")) return "]";
        if (t.equals("[[")) return "]]";
        error("Invalid opening bracket");
        return null;
    }

    public void checkClosingBrackets(LLLNode node, String token) throws LLLCompilationError {
        String expect = opposite(node.opening);
        if (!expect.equals(token))
            error("Expecting " + expect + " but got " + token);
    }

    public void checkArgClose(LLLNode node, String token) throws LLLCompilationError {
        String expect = node.argClose;
        if ((expect == null) || (!expect.equals(token)))
            error("Expecting " + expect + " but got " + token);
        node.argClose = CloseOnNextToken;
    }

    final String CloseOnNextToken = "CloseOnNextToken";
    protected LLLTokenizer st;
    protected LLLNode  node;
    protected Stack<LLLNode> stack;
    protected String token;

    void open(String opcode) {
        if (node != null)
            stack.push(node);
        node= new LLLNode(opcode,token,st.line,st.start,st.getSource());
        if (opcode!=null) {
            node.assetElementsCreated();
            node.elements.add(opcode);
        }
    }

    protected void closeExp() throws LLLCompilationError {
        node.setEndChar(st.start);

        LLLNode child = node;
        if (!stack.empty()) {
            node = stack.pop();
            pushArgument(child);
        }

    }

    protected void closeBracket() throws LLLCompilationError {
        checkClosingBrackets(node, token);
        closeExp();
    }

    protected String getStdOpcode(String op) {
        if (op.equals("**"))
            return "EXP";
        if (op.equals("^")) // in C this is XOR, but here...is EXP
            return "EXP";
        if (op.equals("+"))
            return "ADD";
        if (op.equals("-"))
            return "SUB";
        if (op.equals("/"))
            return "DIV";
        if (op.equals("/"))
            return "MUL";
        if (op.equals("%"))
            return "MOD";
        if (op.equals("&"))
            return "AND";
        if (op.equals("|"))
            return "OR";
        if (op.equals("^"))
            return "XOR";
        if (op.equals("<"))
            return "LT";
        if (op.equals("<"))
            return "LT";
        if (op.equals("S<"))
            return "SLT";
        if (op.equals(">"))
            return "GT";
        if (op.equals("S>"))
            return "SGT";
        if (op.equals("="))
            return "EQ";
        if (op.equals("!"))
            return "ISZERO";
        // >= <= and != must be implemented in the code generator, because they involve negating < > and = with ISZERO
        return op;
    }

    protected void pushArgument(Object arg) throws LLLCompilationError {
        // set an arg
        node.assetElementsCreated();
        node.elements.add(arg); // assumes literal
        if (node.argClose != null) {
            if ((node.argClose.equals("]")) ||
                    (node.argClose.equals("]]"))) // address has passed, now close on next tokeb
            { // do nothing
            }
            else
                closeExp();
        }
    }

    protected void addArgumentOrOpcode() throws LLLCompilationError {
        // Now we can expect an opcode or a literal
        if (node == null)
            error("Unexpected word");
        if (!node.opcodeSet()) {
            node.opcode = getStdOpcode(token);
            // first element is both stored as opcode and as arg list.
            node.assetElementsCreated();
            node.elements.add(token); // assumes literal
        } else
            pushArgument(token);

    }

    LLLNode parse(String data) throws LLLCompilationError {
        try {
            st = new LLLTokenizer(data);

            node = null;
            stack = new Stack<LLLNode>();

            while (st.hasMoreTokens()) {
                //if (node==null)
                //    node= new LLLNode(null,"");
                token = st.nextToken();
                //System.out.println(token);
                if (token.equals("@")) {
                    open("MLOAD");
                    node.argClose = CloseOnNextToken;
                } else
                if (token.equals("@@")) {
                    open("SLOAD");
                    node.argClose = CloseOnNextToken;
                } else
                if (token.equals("(")) {
                    open(null);
                } else if (token.equals("{")) {
                    open("seq");
                } else if (token.equals("[")) {
                    open("mstore");
                    node.argClose = opposite(token);
                } else if (token.equals("[[")) {
                    open("sstore");
                    node.argClose = opposite(token);
                } else if ((token.equals("]")) || (token.equals("]]"))) {
                    checkArgClose(node, token);
                    // Do not close the virtual bracket, auto-close on next arg
                } else if ((token.equals("}")) || (token.equals(")"))) {
                    closeBracket();
                } else
                    addArgumentOrOpcode();

            }
            if (stack.size() != 0)
                error("Open brackets when reaching EOF");
            return node;
        }
        finally {
            st=null;
            node=null;
            stack=null;
            token =null;
        }
    }
}
