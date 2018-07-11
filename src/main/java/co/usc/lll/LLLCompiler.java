package co.usc.lll;

import co.usc.lll.asm.CodeBlock;
import co.usc.lll.asm.EVMAssemblerHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LLLCompiler {

    static public byte[] compileToCode(String s) throws LLLCompilationError {
        LLLCompiler c = new LLLCompiler ();
        c.compile(s);
        return c.block.getCode();
    }

    LLLParser parser;
    LLLCodeGenerator codeGenerator;
    LLLNode rootNode;
    CodeBlock block;
    String source;

    public EVMAssemblerHelper getHelper() {
        return codeGenerator.getHelper();
    }

    public CodeBlock getCodeBlock() {
        return block;
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    public boolean includeFiles(LLLNode parent) throws LLLCompilationError {
        boolean expand = false;
        if (parent instanceof LLLNode) {
            if (parent.opcode.equalsIgnoreCase("INCLUDE")) {
                for(int i=0;i<parent.argCount();i++) {
                    Object a = parent.argAt(i);
                    if (a instanceof String) {
                        String fromFile = null;
                        try {
                            String fileName = (String) a;
                            if ((fileName.length()<2) || (!fileName.startsWith("\"")) || (!fileName.endsWith("\"")))
                                throw new LLLCompilationError("Filename must be a string literal: "+fromFile);
                            fileName = fileName.substring(1,fileName.length()-1);
                            fromFile = readFile(fileName, StandardCharsets.UTF_8);
                            parser = new LLLParser();
                            LLLNode iNode = parser.parse(fromFile);
                            // now parent must be replaced by SEQ, and each file
                            // by a iNode
                            parent.opcode = "SEQ";
                            parent.elements.set(0,parent.opcode);
                            parent.elements.set(i+1,iNode);
                            expand = true;
                        } catch (IOException e) {
                            throw new LLLCompilationError("Include file not found: "+fromFile);
                        }
                    }
                }
            } else
            // recurse
            {
                for(int i=0;i<parent.argCount();i++) {
                    Object a = parent.argAt(i);
                    if (a instanceof LLLNode) {
                        if (includeFiles((LLLNode) a))
                            expand = true;
                    }
                }
            }
        }
        return  expand;
    }

    public void compile(String s) throws LLLCompilationError {
        source =s;
        parser = new LLLParser();
        codeGenerator = new LLLCodeGenerator();

        rootNode = parser.parse(s);
        boolean expansion = false;
        do {
            expansion = includeFiles(rootNode);
        } while(expansion);
        block= codeGenerator.generateCodeBlock(rootNode);

    }

}
