package jpassport;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;

public class PassportBase {
    HashMap<String, MethodHandle> m_methods = new HashMap<>();

    private MethodHandle m_sumd;

    public PassportBase(HashMap<String, MethodHandle> methods) {
        m_methods.putAll(methods);
        init();
        System.out.println("xx");
    }

    private void init() {
        String ss = "sumd";
        m_sumd = m_methods.get(ss);

//        try {
//            m_sumd.invokeExact(1, 1);
//        }
//        catch (Throwable th)
//        {
//            th.printStackTrace();
//        }
    }
}
