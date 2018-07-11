package co.usc.lll.asm;

public class Label {
        int offset;
        String name;
        CodeBlock block;

        public Label(int v, CodeBlock ablock,String aname) {
            offset =v;
            name = aname;
            block =ablock;
        }

}
