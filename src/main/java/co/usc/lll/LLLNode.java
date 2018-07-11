package co.usc.lll;

import co.usc.lll.asm.SourceRef;

import java.util.ArrayList;
import java.util.List;

public class LLLNode {
    public String opcode;
    public List elements; // arguments can be LLLNodes or literals (BigInteger)
    public String opening;
    public String argClose;
    public int line;
    SourceRef ref ;

    public LLLNode(String aopcode,String aopening,int aline,int start,String source) {
        opcode = aopcode;
        opening= aopening;
        line =aline;
        ref = new SourceRef(0,start,0,source); // position undefined, length not defined yet.
    }

    public void setEndChar(int endChar) {
        ref.length = endChar-ref.startChar;
    }

    public int argCount() {
        return elements.size()-1;
    }

    public Object argAt(int index) {
        return elements.get(index+1);
    }

    public void assetElementsCreated() {
        if (elements ==null)
            elements = new ArrayList();
    }

    boolean opcodeSet() {
        return opcode!=null;
    }
}

