package jpassport.parser;

public class BooleanHolder {
    private boolean value;

    public BooleanHolder(boolean value) {
        this.value = value;
    }

    public BooleanHolder() {
        this.value = false;
    }

    public void set(boolean value) {
        this.value = value;
    }

    public boolean value() {return this.value;}
}
