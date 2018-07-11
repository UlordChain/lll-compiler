package co.usc.lll.asm;

import java.util.ArrayList;
import java.util.List;

public class EVMAssemblerHelper {

    int tagIdCount;
    List<Label> labels = new ArrayList<>();

    public EVMAssemblerHelper() {

    }

    public String getLabelName(int id) {
     Label lab = labels.get(id);
     return lab.name;
    }

    public int findLabelByPos(int pos) {
        int startIndex =0;

        for (int i=startIndex;i<labels.size();i++) {
            if  (labels.get(i).offset==pos) {
                return i;
            }
        }
        return -1;
    }

    public int findLabel(String name) {
      for (int i=0;i<labels.size();i++) {
          if  (labels.get(i).name.equalsIgnoreCase(name)) {
              return i;
          }
      }return -1;
    }

    public void moveLabels(CodeBlock from, CodeBlock to, int len) {
        for (int i=0;i<labels.size();i++) {
            Label label =labels.get(i);
            if  (label.block==from) {
                label.block = to;
                if (label.offset <0)
                 throw new RuntimeException("Invalid label");
                label.offset +=len;
            }
        }
    }

    public int getNewLabel(String name) {
        tagIdCount++;
        labels.add(new Label(-1,null,name)); // default no position
        return tagIdCount-1;
    }

    public void setLabelPosition(int id,CodeBlock ablock,int position) {
        Label label = labels.get(id);
        label.offset = position;
        label.block = ablock;
    }

    public void assign(byte[] data, int ofs, int i) {
        data[ofs+3] = (byte) (i & 0xff);
        data[ofs+2] = (byte) ((i>>8) & 0xff);
        data[ofs+1] = (byte) ((i>>16) & 0xff);
        data[ofs+0] = (byte) ((i>>24) & 0xff);
    }

    public boolean performFixUp(CodeBlock block) {
        boolean allFilled  = true;
        if (block==null)
            return true;

        for(int i=0;i<block.tags.size();i++) {
            CodeTag c = block.tags.get(i);
            Label dest = labels.get(c.id);
            // if there is a reference to an external label, we don't fill it
            if (dest.offset <0)
                allFilled = false;
            else {
                // the block of the label MUST be the block passed as argument,
                // if not then blocks have not been merged
                if (dest.block!=block)
                    allFilled = false;
                else
                    assign(block.code, c.position, dest.offset);
            }
        }
        return allFilled;
    }
}
