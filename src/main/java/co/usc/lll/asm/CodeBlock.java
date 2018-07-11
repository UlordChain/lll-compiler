package co.usc.lll.asm;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static co.usc.lll.asm.OpCode.PUSH1;

public class CodeBlock {
    byte[] code ; // = new byte[0];
    List<CodeTag> tags = new ArrayList<>();
    List<SourceRef> refs = new ArrayList<>();
    Set<Integer> calledFuncs = new HashSet<>();
    public boolean reverts = false;


    public CodeBlock(SourceRef topRef) {
        if ((topRef!=null) && (topRef.length>0))  {
            topRef = topRef.cloneSourceRef();
            topRef.position =0;
            refs.add(topRef);
        }

    }

    public void addTag(int position,int id) {
        CodeTag c = new CodeTag(position,id);
        tags.add(c);
    }

    public String getSourceRefText(int pos, int[] fromIndexVec) {
        int fromIndex =0;
        if (fromIndexVec!=null)
            fromIndex = fromIndexVec[0];
         for(int i=fromIndex;i<refs.size();i++) {
            SourceRef ref = refs.get(i);

             if (ref.position==pos) {
                 if (fromIndexVec != null)
                     fromIndexVec[0] = i + 1;
                 return ref.source.substring(ref.startChar, ref.startChar + ref.length);
             }

             if (ref.position<pos)
                 if (fromIndexVec!=null)
                     fromIndexVec[0] = i+1;
         }
        return null;
    }

    public int getTagIdByPos(int pos, int[] fromIndexVec) {
        int fromIndex =0;
        if (fromIndexVec!=null)
            fromIndex = fromIndexVec[0];

        for(int i=fromIndex;i<tags.size();i++) {
            CodeTag tag = tags.get(i);
            if (tag.position==pos) {
                if (fromIndexVec!=null)
                    fromIndexVec[0] = i+1;
                return tag.id;
            }

            if (tag.position<pos)
                if (fromIndexVec!=null)
                    fromIndexVec[0] = i+1;

        }
        return -1;
    }

    public void addSourceRef(int position,int startChar,int length,String source) {
        SourceRef c = new SourceRef(position,startChar,length,source);
        refs.add(c);
    }

    public byte[] getCode() {
        if (code==null)
            code = new byte[0]; // better for the users
        return code;
    }

    byte[] combine(byte[] one, byte[] two) {

        byte[] combined = new byte[one.length + two.length];

        System.arraycopy(one, 0, combined, 0, one.length);
        System.arraycopy(two, 0, combined, one.length, two.length);
        return combined;
    }

    ByteArrayOutputStream bOut;

    public int writeOffset() {
        int ofs =0;

        if (code!=null)
            ofs = code.length;

        if (bOut!=null)
            ofs +=bOut.size();
        return ofs;
    }


    public void writePushLabelRef() {
        int pushOpcode = 4 + PUSH1.val() - 1; // four bytes ref
        bOut.write(pushOpcode);
        writeLabelRef();
    }

    public void writePushByte(int b) {
        int pushOpcode = PUSH1.val() ;
        bOut.write(pushOpcode);
        bOut.write(b);
    }
    public void writePushInt(int i) {
        int pushOpcode = PUSH1.val()+3;
        bOut.write(pushOpcode);
        bOut.write( (i>>24) & 0xff);
        bOut.write( (i>>16) & 0xff);
        bOut.write( (i>>8) & 0xff);
        bOut.write( i & 0xff );

    }

    public void writeLabelRef() {
        bOut.write(0);
        bOut.write(0);
        bOut.write(0);
        bOut.write(0);
    }

    public void startWrite() {
        bOut = new ByteArrayOutputStream();

    }

    public ByteArrayOutputStream writer() {
        if (bOut==null)
           throw new RuntimeException("call startWrite before writting");
        return bOut;
    }

    public void endWrite() {
        if (code==null)
            code = bOut.toByteArray();
        else
            code = combine(code,bOut.toByteArray());
        bOut = null;
    }

    public void append(EVMAssemblerHelper helper,CodeBlock c) {
        if (code==null) {
            if (c==null)
                return; // ?
            code = c.code;
            tags.addAll(c.tags); //
            helper.moveLabels(c,this,0);
            refs.addAll(c.refs);
            calledFuncs.addAll(c.calledFuncs);
            reverts = c.reverts;
            return;
        }

        int prevCodeLen = code.length;
        byte[] newCode = combine(code,c.code);
        int len =tags.size();
        tags.addAll(c.tags);
        for(int i=len;i<tags.size();i++) {
            tags.get(i).position += prevCodeLen;
        }
        code = newCode;
        helper.moveLabels(c,this,prevCodeLen);
        // Now we move all labels from c to this block

        // Now move the refs
        int refLen =refs.size();
        refs.addAll(c.refs);
        for(int i=refLen;i<refs.size();i++) {
            refs.get(i).position += prevCodeLen;
        }
        calledFuncs.addAll(c.calledFuncs);
        reverts = c.reverts;
    }

    public Set<Integer> getCalledFuncs() {
        return calledFuncs;
    }

    public void addCalledFunc(int id) {
        calledFuncs.add(new Integer(id));
    }

    public void writePushTag(int id) {
        addTag(writeOffset()+1,id);
        writePushLabelRef();
    }
}
