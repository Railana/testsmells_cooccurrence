package org.apache.cayenne.testdo.inheritance_vertical.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.cayenne.exp.property.EntityProperty;
import org.apache.cayenne.exp.property.PropertyFactory;
import org.apache.cayenne.testdo.inheritance_vertical.IvRoot;

/**
 * Class _IvSub3 was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _IvSub3 extends IvRoot {

    private static final long serialVersionUID = 1L;

    public static final String ID_PK_COLUMN = "ID";

    public static final EntityProperty<IvRoot> IV_ROOT = PropertyFactory.createEntity("ivRoot", IvRoot.class);


    protected Object ivRoot;

    public void setIvRoot(IvRoot ivRoot) {
        setToOneTarget("ivRoot", ivRoot, true);
    }

    public IvRoot getIvRoot() {
        return (IvRoot)readProperty("ivRoot");
    }

    protected abstract void onPrePersist();

    @Override
    public Object readPropertyDirectly(String propName) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch(propName) {
            case "ivRoot":
                return this.ivRoot;
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
            case "ivRoot":
                this.ivRoot = val;
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
        out.writeObject(this.ivRoot);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.ivRoot = in.readObject();
    }

}
