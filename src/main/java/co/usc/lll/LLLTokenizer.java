package co.usc.lll;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class LLLTokenizer implements Enumeration<Object> {
    private int currentPosition;
    private int newPosition;
    private int maxPosition;
    public int line =0;
    private String str;
    private String delimiters = " \t\n\r\f";

    // minus sign (-) is special because some macros use it as word separation in an identifier
    // such as short-name.

    private String symbols = "(){}[]@|&%#!=><*^/%";

    public String getSource() {
        return str;
    }

    public LLLTokenizer(String str) {
        currentPosition = 0;
        newPosition = -1;
        this.str = str;
        maxPosition = str.length();

    }

    private int skipDelimiters(int startPos) {
        int position = startPos;
        boolean inCommnet = false;
        while (position < maxPosition) {
            char c = str.charAt(position);
            if (c=='\n') {
                line++;
                inCommnet = false;
            }
            if (!inCommnet)
            if (c==';') // comments in any place
                inCommnet= true;
            else
            if (delimiters.indexOf(c) < 0)
                break;
            position++;
        }
        return position;
    }

    /**
     * Skips ahead from startPos and returns the index of the next delimiter
     * character encountered, or maxPosition if no such delimiter is found.
     */
    boolean prevIs(char a, char b,String c) {
        return (c.charAt(0)==a) && (c.charAt(1)==b);
    }

    private int scanToken(int startPos) {
        int position = startPos;
        char prevSymbol = '\0';
        boolean inCommnet = false;
        boolean inString = false;
        while (position < maxPosition) {
            char c = str.charAt(position);
            if (c=='\n') {
                line++;
                inCommnet = false;
            }
            if (!inCommnet) {
                if (c =='"') {
                    inString =!inString;
                }
                else
                if (c == ';') // comments in any place
                    inCommnet = true;
                else
                // Only stop advancing identifier if a delimiter is found
                if  (delimiters.indexOf(c) >= 0) {
                       break;
                }
                if (!inString) {
                    // Now let's see if it is a special symbol
                    if (symbols.indexOf(c) >= 0) {// include the symbol
                        if (prevSymbol == 'A') // marks a non-symbol
                            //   if (c!='-') // special case for alpha-alpha
                            break;

                        if ((prevIs(prevSymbol, c, "[[")) ||
                                (prevIs(prevSymbol, c, "]]")) ||
                                (prevIs(prevSymbol, c, "@@")) ||
                                (prevIs(prevSymbol, c, "||")) ||
                                (prevIs(prevSymbol, c, ">=")) ||
                                (prevIs(prevSymbol, c, "<=")) ||
                                (prevIs(prevSymbol, c, "!=")) ||
                                (prevIs(prevSymbol, c, "**")) ||
                                (prevIs(prevSymbol, c, "&&"))
                                ) {
                            // [[ take as a simble symbol
                            prevSymbol = (char) 1; // any char will do
                        } else if (prevSymbol != '\0') // two symbols in a row? break after the first
                            break;
                        else
                            prevSymbol = c;

                    } else {
                        if ((prevSymbol != '\0') && (prevSymbol != 'A'))// a non-symbol following a symbols? break after the first
                            break;
                        prevSymbol = 'A'; // marks a non-symbol
                    }
                }
            }
            position++;

        }
            /*
            if ((startPos == position)) {
                char c = str.charAt(position);
                    if ((delimiters.indexOf(c) >= 0))
                        position++;
                }
                */
        return position;
    }

    public boolean hasMoreTokens() {
        newPosition = skipDelimiters(currentPosition);
        return (newPosition < maxPosition);
    }

    int start;
    public void nextTokenSearch() {
        currentPosition = (newPosition >= 0) ?
                newPosition : skipDelimiters(currentPosition);

        newPosition = -1;
        if (currentPosition >= maxPosition)
            throw new NoSuchElementException();
        start = currentPosition;
        currentPosition = scanToken(currentPosition);
    }

    public String getTokenFound() {
        return str.substring(start, currentPosition);
    }

    public String nextToken() {
        nextTokenSearch();
        return getTokenFound();
    }

    public boolean hasMoreElements() {
        return hasMoreTokens();
    }

    public Object nextElement() {
        return nextToken();
    }

}


