package spreadsheet.model;

/** The computed value of a cell. */
public sealed interface Value permits Value.NumberValue, Value.TextValue, Value.ErrorValue, Value.EmptyValue {

    /** What the grid shows. */
    String display();

    record NumberValue(double number) implements Value {
        @Override
        public String display() {
            if (number == Math.rint(number) && Math.abs(number) < 1e15) return String.valueOf((long) number);
            String s = String.format("%.10f", number).replaceAll("0+$", "");
            return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        }
    }

    record TextValue(String text) implements Value {
        @Override
        public String display() {
            return text;
        }
    }

    record ErrorValue(String code) implements Value {
        public static final ErrorValue DIV_ZERO = new ErrorValue("#DIV/0!");
        public static final ErrorValue REF = new ErrorValue("#REF!");
        public static final ErrorValue NAME = new ErrorValue("#NAME?");
        public static final ErrorValue PARSE = new ErrorValue("#PARSE!");
        public static final ErrorValue VALUE = new ErrorValue("#VALUE!");
        public static final ErrorValue CYCLE = new ErrorValue("#CYCLE!");

        @Override
        public String display() {
            return code;
        }
    }

    record EmptyValue() implements Value {
        public static final EmptyValue INSTANCE = new EmptyValue();

        @Override
        public String display() {
            return "";
        }
    }
}
