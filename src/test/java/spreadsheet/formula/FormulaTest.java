package spreadsheet.formula;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import spreadsheet.formula.Token.Type;
import spreadsheet.model.Spreadsheet;
import spreadsheet.model.Value;

class FormulaTest {

    private final Spreadsheet sheet = new Spreadsheet();

    private String eval(String formula) {
        sheet.setCell("Z100", formula);
        return sheet.valueAt("Z100").display();
    }

    @Test
    void tokenizerRecognisesEveryTokenType() throws ParseException {
        List<Type> types = Tokenizer.tokenize("SUM(a1:B2, 2.5) + -3 * 4 / 5 ^ 6").stream().map(Token::type).toList();
        assertEquals(List.of(Type.NAME, Type.LPAREN, Type.CELL, Type.COLON, Type.CELL, Type.COMMA, Type.NUMBER,
                Type.RPAREN, Type.PLUS, Type.MINUS, Type.NUMBER, Type.STAR, Type.NUMBER, Type.SLASH, Type.NUMBER,
                Type.CARET, Type.NUMBER, Type.EOF), types);
        assertThrows(ParseException.class, () -> Tokenizer.tokenize("1 $ 2"));
        assertThrows(ParseException.class, () -> Tokenizer.tokenize("1.2.3"));
    }

    @Test
    void precedenceAndAssociativity() {
        assertEquals("14", eval("=2+3*4"));
        assertEquals("20", eval("=(2+3)*4"));
        assertEquals("512", eval("=2^3^2"));  // right-associative: 2^(3^2)
        assertEquals("3", eval("=-2+5"));
        assertEquals("-8", eval("=-2^3"));
        assertEquals("1", eval("=10-5-4"));   // left-associative
        assertEquals("2.5", eval("=10/4"));
    }

    @Test
    void badSyntaxIsAParseError() {
        for (String f : List.of("=2+", "=(1+2", "=1 2", "=SUM(", "=A1:B2", "=*3", "=A0")) {
            assertEquals("#PARSE!", eval(f), f);
        }
    }

    @Test
    void functionsOverRangesSkipEmptyCellsAndText() {
        sheet.setCell("A1", "10");
        sheet.setCell("A2", "20");
        sheet.setCell("A3", "hello");
        // A4 is empty
        sheet.setCell("A5", "30");
        assertEquals("60", eval("=SUM(A1:A5)"));
        assertEquals("20", eval("=AVERAGE(A1:A5)"));
        assertEquals("10", eval("=MIN(A1:A5)"));
        assertEquals("30", eval("=MAX(A5:A1)"));  // corners in either order
        assertEquals("3", eval("=COUNT(A1:A5)"));
        assertEquals("65", eval("=SUM(A1:A5, 5)"));
        assertEquals("4", eval("=ABS(-4)"));
        assertEquals("3.14", eval("=ROUND(3.14159, 2)"));
        assertEquals("#DIV/0!", eval("=AVERAGE(A3:A4)"));
    }

    @Test
    void everyErrorType() {
        sheet.setCell("A1", "text");
        assertEquals("#DIV/0!", eval("=1/0"));
        assertEquals("#REF!", eval("=AA1+1"));       // column outside the 26-column grid
        assertEquals("#REF!", eval("=A500"));
        assertEquals("#NAME?", eval("=FOO(1)"));
        assertEquals("#PARSE!", eval("=1+"));
        assertEquals("#VALUE!", eval("=A1*2"));      // text in arithmetic
        assertEquals("#VALUE!", eval("=ROUND(1)"));  // wrong number of arguments
        assertInstanceOf(Value.ErrorValue.class, sheet.valueAt("Z100"));
    }

    @Test
    void errorsPassThroughAChain() {
        sheet.setCell("A1", "=1/0");
        sheet.setCell("B1", "=A1+1");
        sheet.setCell("C1", "=SUM(B1, 5)");
        assertEquals("#DIV/0!", sheet.valueAt("C1").display());
        sheet.setCell("A1", "2");
        assertEquals("8", sheet.valueAt("C1").display());
    }
}
