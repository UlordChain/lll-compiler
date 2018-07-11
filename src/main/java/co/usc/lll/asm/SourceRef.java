package co.usc.lll.asm;

public class SourceRef {
    public int position;
    public int startChar;
    public int length;
    public String source;

    public SourceRef cloneSourceRef() {
        SourceRef ret = new SourceRef(position,startChar,length,source);
        return ret;
    }

    public SourceRef(int position,int startChar,int length,String source) {
        this.position = position;
        this.startChar = startChar;
        this.length = length;
        this.source = source;
    }
}
