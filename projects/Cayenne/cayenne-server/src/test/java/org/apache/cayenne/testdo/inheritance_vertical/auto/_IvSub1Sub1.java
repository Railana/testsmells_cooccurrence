package org.apache.cayenne.testdo.inheritance_vertical.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.cayenne.exp.property.PropertyFactory;
import org.apache.cayenne.exp.property.StringProperty;
import org.apache.cayenne.testdo.inheritance_vertical.IvSub1;

/**
 * Class _IvSub1Sub1 was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _IvSub1Sub1 extends IvSub1 {

    private static final long serialVersionUID = 1L;

    public static final String ID_PK_COLUMN = "ID";

    public static final StringProperty<String> SUB1SUB1NAME = PropertyFactory.createString("sub1Sub1Name", String.class);

    protected String sub1Sub1Name;


    public void setSub1Sub1Name(String sub1Sub1Name) {
        beforePropertyWrite("sub1Sub1Name", this.sub1Sub1Name, sub1Sub1Name);
        this.sub1Sub1Name = sub1Sub1Name;
    }

    public String getSub1Sub1Name() {
        beforePropertyRead("sub1Sub1Name");
        return this.sub1Sub1Name;
    }

    @Override
    public Object readPropertyDirectly(String propName) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch(propName) {
            case "sub1Sub1Name":
                return this.sub1Sub1Name;
            default:
                return super.readPropertyDirectly(propName);
        }
    }

    @Override
    public void writePropertyDirectly(String propName, Object val) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch (propName) {
            case "sub1Sub1Name":
                this.sub1Sub1Name = (String)val;
                break;
            default:
                super.writePropertyDirectly(propName, val);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        writeSerialized(out);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        readSerialized(in);
    }

    @Override
    protected void writeState(ObjectOutputStream out) throws IOException {
        super.writeState(out);
        out.writeObject(this.sub1Sub1Name);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.sub1Sub1Name = (String)in.readObject();
    }

}
