/*
 *    Copyright (c) Open Connectivity Foundation (OCF), AllJoyn Open Source
 *    Project (AJOSP) Contributors and others.
 *    
 *    SPDX-License-Identifier: Apache-2.0
 *    
 *    All rights reserved. This program and the accompanying materials are
 *    made available under the terms of the Apache License, Version 2.0
 *    which accompanies this distribution, and is available at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    
 *    Copyright (c) Open Connectivity Foundation and Contributors to AllSeen
 *    Alliance. All rights reserved.
 *    
 *    Permission to use, copy, modify, and/or distribute this software for
 *    any purpose with or without fee is hereby granted, provided that the
 *    above copyright notice and this permission notice appear in all
 *    copies.
 *    
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
 *    WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
 *    WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
 *    AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL
 *    DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 *    PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 *    TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *    PERFORMANCE OF THIS SOFTWARE.
*/

package org.alljoyn.bus;

import org.alljoyn.bus.defs.BusObjectInfo;
import org.alljoyn.bus.defs.InterfaceDef;
import org.alljoyn.bus.defs.MethodDef;
import org.alljoyn.bus.defs.PropertyDef;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alljoyn.bus.annotation.BusProperty;

/**
 * A bus object that exists and is managed by some other connection to
 * the bus. Use ProxyBusObject to call methods on remote objects.
 */
public class ProxyBusObject {

    private static final int AUTO_START = 0x02;
    private static final int ENCRYPTED = 0x80;

    /** The bus the remote object is connected to. */
    private BusAttachment bus;

    /** Well-known or unique name of remote object. */
    private String busName;

    /** Object path. */
    private String objPath;

    /** Native proxy bus object handle. */
    private long handle;

    /** Remote interfaces proxy. */
    private Object proxy;

    private int replyTimeoutMsecs;

    private int flags;

    private Method busConnectionLost;

    private List<InterfaceDef> interfaceDefs;

    /**
     * Construct a ProxyBusObject.
     *
     * @param busAttachment  The connection the remote object is on.
     * @param busName        Well-known or unique bus name of remote object.
     * @param objPath        Object path of remote object.
     * @param sessionId      The session ID corresponding to the connection to the object.
     * @param busInterfaces  A list of BusInterfaces that this proxy should respond to.
     */
    protected ProxyBusObject(BusAttachment busAttachment, String busName, String objPath, int sessionId,
                             Class<?>[] busInterfaces) {
        this(busAttachment, busName, objPath, sessionId, busInterfaces, false);
    }

    /**
     * Construct a ProxyBusObject.
     *
     * @param busAttachment  The connection the remote object is on.
     * @param busName        Well-known or unique bus name of remote object.
     * @param objPath        Object path of remote object.
     * @param sessionId      The session ID corresponding to the connection to the object.
     * @param busInterfaces  A list of BusInterfaces that this proxy should respond to.
     * @param secure         the security mode for the remote object
     */
    protected ProxyBusObject(BusAttachment busAttachment, String busName, String objPath, int sessionId,
                             Class<?>[] busInterfaces, boolean secure) {
        this.bus = busAttachment;
        this.busName = busName;
        this.objPath = objPath;
        this.flags = 0;
        create(busAttachment, busName, objPath, sessionId, secure);
        replyTimeoutMsecs = 25000;
        proxy = Proxy.newProxyInstance(busInterfaces[0].getClassLoader(), busInterfaces, new Handler());
        try {
            busConnectionLost =
                getClass().getDeclaredMethod("busConnectionLost", String.class);
            busConnectionLost.setAccessible(true);
        } catch (NoSuchMethodException ex) {
            /* This will not happen */
        }
    }

    /**
     * Construct a ProxyBusObject using dynamic interface definitions.
     * The proxy will use the GenericInterface.class to relay supported bus methods and properties.
     *
     * @param busAttachment  The connection the remote object is on.
     * @param busName        Well-known or unique bus name of remote object.
     * @param objPath        Object path of remote object.
     * @param sessionId      The session ID corresponding to the connection to the object.
     * @param interfaceDefs  A list of dynamic interface definitions that this proxy supports.
     * @param secure         the security mode for the remote object
     */
    public ProxyBusObject(BusAttachment busAttachment, String busName, String objPath, int sessionId,
                          List<InterfaceDef> interfaceDefs, boolean secure) {
        this.bus = busAttachment;
        this.busName = busName;
        this.objPath = objPath;
        this.flags = 0;
        create(busAttachment, busName, objPath, sessionId, secure);
        replyTimeoutMsecs = 25000;

        this.interfaceDefs = interfaceDefs;
        Class<?>[] busInterfaces = new Class<?>[] {GenericInterface.class};
        proxy = Proxy.newProxyInstance(busInterfaces[0].getClassLoader(), busInterfaces, new DynamicHandler(interfaceDefs));

        try {
            busConnectionLost = getClass().getDeclaredMethod("busConnectionLost", String.class);
            busConnectionLost.setAccessible(true);
        } catch (NoSuchMethodException ex) {
            // This will not happen
        }
    }

    /** Allocate native resources. */
    private native void create(BusAttachment busAttachment, String busName, String objPath, int sessionId, boolean secure);

    /** Release native resources. */
    private synchronized native void destroy();

    /** Called by native code to lazily add an interface when a proxy method is invoked. */
    protected int addInterface(String name) throws AnnotationBusException {
        // Create interface description based on dynamic or static interface definitions
        if (interfaceDefs != null && name != null) {
            InterfaceDef intfDef = BusObjectInfo.getInterface(interfaceDefs, name);
            if (intfDef != null) {
                InterfaceDescription desc = new InterfaceDescription(null);
                Status status = desc.create(bus, intfDef);
                return (status == Status.BUS_IFACE_ALREADY_EXISTS) ? Status.OK.getErrorCode() : status.getErrorCode();
            }
        } else if (name != null) {
            for (Class<?> intf : proxy.getClass().getInterfaces()) {
                if (name.equals(InterfaceDescription.getName(intf))) {
                    InterfaceDescription desc = new InterfaceDescription();
                    Status status = desc.create(bus, intf);
                    return status.getErrorCode();
                }
            }
        }
        return Status.BUS_NO_SUCH_INTERFACE.getErrorCode();
    }

    /** Perform a method call on the remote object. */
    private native Object methodCall(BusAttachment busAttachment, String interfaceName,
            String methodName, String inputSig, Type outType, Object[] args, int replyTimeoutMsecs,
            int flags) throws BusException;

    /** Get a property of the remote object. */
    private native Variant getProperty(BusAttachment busAttachment, String interfaceName,
            String propertyName) throws BusException;

    private native Map<String, Variant> getAllProperties(BusAttachment busAttachment, Type outType, String interfaceName) throws BusException;

    /** Set a property of the remote object. */
    private native void setProperty(BusAttachment busAttachment, String interfaceName,
            String propertyName, String signature, Object value) throws BusException;

    /** Is the remote object for this proxy bus object secure. */
    private native boolean isProxyBusObjectSecure();

    /** Enable property caching for this proxy bus object. */
    public native void enablePropertyCaching();

    /** The invocation handler for the bus interfaces. */
    private class Handler implements InvocationHandler {

        private class Invocation {
            public Method method;

            public boolean isMethod;
            public boolean isGet;

            public String inputSig;
            public String outSig;

            public String interfaceName;
            public String methodName;

            public Type genericReturnType;
            public Class<?> returnType;

            public int timeout;

            public Invocation(Method method) throws BusException {
                this.method = method;
                if (method.getAnnotation(BusProperty.class) != null) {
                    this.isGet = method.getName().startsWith("get");
                    this.outSig = InterfaceDescription.getPropertySig(method);
                } else {
                    this.isMethod = true;
                    this.outSig = InterfaceDescription.getOutSig(method);
                    this.inputSig = InterfaceDescription.getInputSig(method);
                }
                this.interfaceName = InterfaceDescription.getName(method.getDeclaringClass());
                this.methodName = InterfaceDescription.getName(method);
                this.genericReturnType = method.getGenericReturnType();
                this.returnType = method.getReturnType();
                this.timeout = InterfaceDescription.getTimeout(method);
            }
        };

        private Map<String, List<Invocation>> invocationCache;

        public Handler() {
            this.invocationCache = new HashMap<String, List<Invocation>>();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws BusException {
            /*
             * Some notes on performance.
             *
             * Reflection is very expensive.  So first pass at optimization is to cache the
             * reflection calls that lookup names and annotations the first time the method is
             * invoked.
             *
             * Using a Method as a HashMap key is expensive.  Using method.getName() as the key
             * is less expensive.  But method names may not be unique (they can be overloaded), so
             * need to fall back to Method.equals if more than one method with the same name exists.
             */
            Invocation invocation = null;
            String methodName = method.getName();
            List<Invocation> invocationList = invocationCache.get(methodName);
            if (invocationList != null) {
                if (invocationList.size() == 1) {
                    /* The fast path. */
                    invocation = invocationList.get(0);
                } else {
                    /* The slow path.  Two Java methods exist with the same name for this proxy. */
                    for (Invocation i : invocationList) {
                        if (method.equals(i.method)) {
                            invocation = i;
                            break;
                        }
                    }
                    if (invocation == null) {
                        invocation = new Invocation(method);
                        invocationList.add(invocation);
                    }
                }
            } else {
                /*
                 * The very slow path.  The first time a proxy method is invoked.
                 *
                 * Walk through all the methods looking for ones that match the invoked method name.
                 * This creates a list of all the cached invocation information that we'll use later
                 * on the next method call.
                 */
                invocationList = new ArrayList<Invocation>();
                for (Class<?> i : proxy.getClass().getInterfaces()) {
                    for (Method m : i.getMethods()) {
                        if (methodName.equals(m.getName())) {
                            Invocation in = new Invocation(m);
                            invocationList.add(in);
                            if (method.equals(in.method)) {
                                invocation = in;
                            }
                        }
                    }
                }
                if (invocation == null) {
                    try {
                        Class<Object> objectClass = Object.class;
                        if (method.equals(objectClass.getMethod("toString"))) {
                            return proxyToString(proxy);
                        }
                        if (method.equals(objectClass.getMethod("equals",
                                objectClass))) {
                            return proxy == args[0];
                        }
                        if (method.equals(objectClass.getMethod("hashCode"))) {
                            return System.identityHashCode(proxy);
                        }
                    } catch (NoSuchMethodException e) {
                        /*
                         * If NoSuchMethodException is encountered the code will
                         * drop through and throw a BusException.
                         */
                    }
                    throw new BusException("No such method: " + method);
                }
                invocationCache.put(methodName, invocationList);
            }

            Object value = null;
            if (invocation.isMethod) {
                value = methodCall(bus,
                                   invocation.interfaceName,
                                   invocation.methodName,
                                   invocation.inputSig,
                                   invocation.genericReturnType,
                                   args,
                                   invocation.timeout == -1 ? replyTimeoutMsecs : invocation.timeout,
                                   flags);
            } else {
                if (invocation.isGet) {
                    Variant v = getProperty(bus,
                                            invocation.interfaceName,
                                            invocation.methodName);
                    value = v.getObject(invocation.genericReturnType);
                } else {
                    setProperty(bus,
                                invocation.interfaceName,
                                invocation.methodName,
                                invocation.outSig,
                                args[0]);
                }
            }

            /*
             * The JNI layer can't perform complete type checking (at least not easily),
             * so this extra code is here.  The conditions below are taken from the
             * InvocationHandler documentation.
             */
            boolean doThrow = false;
            Class<?> returnType = invocation.returnType;
            if (value == null) {
                doThrow = returnType.isPrimitive() && !returnType.isAssignableFrom(Void.TYPE);
            } else if (returnType.isPrimitive()) {
                if ((returnType.isAssignableFrom(Byte.TYPE) && !(value instanceof Byte))
                    || (returnType.isAssignableFrom(Short.TYPE) && !(value instanceof Short))
                    || (returnType.isAssignableFrom(Integer.TYPE) &&  !(value instanceof Integer))
                    || (returnType.isAssignableFrom(Long.TYPE) && !(value instanceof Long))
                    || (returnType.isAssignableFrom(Double.TYPE) && !(value instanceof Double))
                    || (returnType.isAssignableFrom(Boolean.TYPE) && !(value instanceof Boolean))) {
                    doThrow = true;
                }
            } else if (!returnType.isAssignableFrom(value.getClass())) {
                doThrow = true;
            }
            if (doThrow) {
                throw new MarshalBusException("cannot marshal '" + invocation.outSig + "' into " + returnType);
            }
            return value;
        }
    }

    /**
     * Release resources immediately.
     *
     * Normally, when all references are removed to a given object, the Java
     * garbage collector notices the fact that the object is no longer required
     * and will destory it.  This can happen at the garbage collector's leisure
     * an so any resources held by the object will not be released until "some
     * time later" after the object is no longer needed.
     *
     * Often, in test programs, we cycle through many BusAttachments in a very
     * short time, and if we rely on the garbage collector to clean up, we can
     * fairly quickly run out of scarce underlying resources -- especially file
     * descriptors.
     *
     * We provide an explicity release() method to allow test programs to release
     * the underlying resources immediately.  The garbage collector will still
     * call finalize, but the resrouces held by the underlying C++ objects will
     * go away immediately.
     *
     * It is a programming error to call another method on the ProxyBusObject
     * after the release() method has been called.
     */
    public void release() {
        destroy();
    }

    /**
     * Release native resources.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }

    /**
     * Gets the bus name.
     *
     * @return the bus name
     */
    public String getBusName() {
        return busName;
    }

    /**
     * Gets the object path.
     *
     * @return the object path
     */
    public String getObjPath() {
        return objPath;
    }

    /**
     * Gets a proxy to an interface of this remote bus object.
     *
     * @param <T> any class implementation of a interface annotated with AllJoyn interface annotations
     * @param intf one of the interfaces supplied when the proxy bus object was
     *             created. For a proxy bus object that was created using interface definitions
     *             (InterfaceDef list), the intf parameter should be the GenericInterface.
     * @return the proxy implementing the interface
     * @see BusAttachment#getProxyBusObject(String, String, int, Class[])
     * @see BusAttachment#getProxyBusObject(String, String, int, List, boolean)
     */
    public <T> T getInterface(Class<T> intf) {
        @SuppressWarnings(value = "unchecked")
        T p = (T) proxy;
        return p;
    }

    /**
     * Sets the reply timeout for method invocations on this remote bus object.
     * This affects all future method invocations of this bus object.  The
     * default reply timeout is 25 seconds.
     *
     * @param timeoutMsecs the timeout to wait for a reply
     */
    public void setReplyTimeout(int timeoutMsecs) {
        replyTimeoutMsecs = timeoutMsecs;
    }

    /**
     * Tells the bus to start an application to handle a method invocation of
     * this bus object if needed.
     * This affects all future method invocations of this bus object.  The
     * default behavior is to not start an application.
     *
     * @param autoStart if {@code true} the bus should automatically start an
     *                  application to handle the method
     */
    public void setAutoStart(boolean autoStart) {
        this.flags = autoStart ? this.flags | AUTO_START : this.flags & ~AUTO_START;
    }

    /**
     * Indicates if the remote object for this proxy bus object is secure.
     *
     * @return  true if the object is secure
     */
    public boolean isSecure() {
        return isProxyBusObjectSecure();
    }

    /**
     * Get a property from an interface on the remote object.
     *
     * @param <T> any class implementation of a interface annotated with AllJoyn interface annotations
     * @param iface the interface that the property exists on
     * @param propertyName the name of the property
     * @return Variant containing the value of the property
     * @throws BusException if the named property doesn't exist
     */
    public <T> Variant getProperty(Class<T> iface, String propertyName) throws BusException {
        return getProperty(bus, InterfaceDescription.getName(iface), propertyName);
    }

    /**
     * Set a property on an interface on the remote object.
     *
     * @param <T> any class implementation of a interface annotated with AllJoyn interface annotations
     * @param iface the interface that the property exists on
     * @param propertyName the name of the property
     * @param value the value for the property
     * @throws BusException if the named property doesn't exist or cannot be set
     */
    public <T> void setProperty(Class<T> iface, String propertyName, Variant value) throws BusException {
        setProperty(bus, InterfaceDescription.getName(iface), propertyName, value.getSignature(), value.getValue());;
    }

    /**
     * Get all properties from an interface on the remote object.
     *
     * @param <T> any class implementation of a interface annotated with AllJoyn interface annotations
     * @param iface the interface
     * @return a Map of name/value associations
     * @throws BusException if request cannot be honored
     */
    public <T> Map<String, Variant> getAllProperties(Class<T> iface) throws BusException {
        return getAllProperties(InterfaceDescription.getName(iface));
    }

    /**
     * Get all properties from an interface on the remote object.
     *
     * @param interfaceName the name of the interface
     * @return a Map of name/value associations
     * @throws BusException if request cannot be honored
     */
    public Map<String, Variant> getAllProperties(String interfaceName) throws BusException {
        Map<String, Variant> map = null;
        try {
            Type returnType;
            returnType = org.alljoyn.bus.ifaces.Properties.class.getMethod("GetAll", String.class).getGenericReturnType();
            map = getAllProperties(bus, returnType, interfaceName);
        } catch (NoSuchMethodException e) {
            /* This will not happen */
            System.err.println("Unexpected NoSuchMethodException for the GetAll method");
        }
        return map;
    }

    /**
     * Function to register a handler for property change events.
     * Note that registering the same handler callback for the same
     * interface will overwrite the previous registration.  The same
     * handler callback may be registered for several different
     * interfaces simultaneously.
     *
     * @param iface             Remote object's interface on which the property is defined.
     * @param properties        The name of the properties to monitor (NULL for all).
     * @param listener          Reference to the object that will receive the callback.
     * @return
     *      - #ER_OK if the handler was registered successfully
     *      - #ER_BUS_NO_SUCH_INTERFACE if the specified interfaces does not exist on the remote object.
     *      - #ER_BUS_NO_SUCH_PROPERTY if the property does not exist
     */
    public native Status registerPropertiesChangedListener(String iface, String[] properties,
                                                           PropertiesChangedListener listener);

    /**
     * Function to unregister a handler for property change events.
     *
     * @param iface     Remote object's interface on which the property is defined.
     * @param listener  Reference to the object that used to receive the callback.
     * @return
     *      - #ER_OK if the handler was registered successfully
     *      - #ER_BUS_NO_SUCH_INTERFACE if the specified interfaces does not exist on the remote object.
     */
    public native Status unregisterPropertiesChangedListener(String iface,
                                                             PropertiesChangedListener listener);


    /**
     * The implementation of toString method of proxy objects provided by the InvocationHandler.
     *
     * @param proxy the proxy to generate a string for.
     * @return a string representation of the proxy object.
     */
    static String proxyToString(Object proxy) {
        Class<?> clazz = proxy.getClass();
        Class<?>[] intfs = clazz.getInterfaces();
        String[] names = new String[intfs.length];
        for (int i = 0; i < intfs.length; i++) {
            names[i] = intfs[i].getName();
        }
        return clazz.getCanonicalName() + Arrays.toString(names) + "@"
                + System.identityHashCode(proxy);
    }

    /**
     * The implementation of toString method of a proxy object and its list
     * of related bus interface descriptions provided by the InvocationHandler.
     * This operation is used when the proxy object implements GenericInterface
     * rather then implementing the bus interfaces directly.
     *
     * @param proxy the proxy to generate a string for.
     * @param intfs the bus interface descriptions relating to the proxy
     * @return a string representation of the proxy object and bus interfaces.
     */
    static String proxyToString(Object proxy, List<InterfaceDef> intfs) {
        Class<?> clazz = proxy.getClass();
        String[] names = new String[intfs.size()];
        for (int i = 0; i < intfs.size(); i++) {
            names[i] = intfs.get(i).getName();
        }
        return clazz.getCanonicalName() + Arrays.toString(names) + "@"
                + System.identityHashCode(proxy);
    }


    /**
     * Invocation handler for bus methods and bus properties that are based on dynamic MethodDef
     * and PropertyDef definitions.
     *
     * The handler accepts invoke requests for:
     * <ul>
     *     GenericInterface.methodCall(interfaceName, methodName, args...)
     *     GenericInterface.getProperty(interfaceName, propertyName)
     *     GenericInterface.setProperty(interfaceName, propertyName, arg)
     * </ul>
     *
     * The DynamicHandler is initialized with a list of permitted InterfaceDef. For a requested bus
     * method/property to be called, the corresponding dynamic MethodDef/PropertyDef must be present
     * in this list.
     */
    private class DynamicHandler implements InvocationHandler {

        final private List<InterfaceDef> interfaceDefs;

        /**
         * Constructor.
         *
         * @param interfaceDefs the definitions that describe the interfaces whose methods/properties
         *                      can be called.
         * @throws IllegalArgumentException invalid argument was specified.
         */
        public DynamicHandler(List<InterfaceDef> interfaceDefs) {
            if (interfaceDefs == null) {
                throw new IllegalArgumentException(("Null interfaceDefs"));
            }
            this.interfaceDefs = interfaceDefs;
        }

        /**
         * Send a bus method call, or bus property get/set, based on the specified args and their
         * corresponding MethodDef/PropertyDef definition.
         *
         * @param proxy The proxy being called (instance of GenericInterface).
         * @param method The proxy Method being called. Should be one of: GenericInterface.methodCall(...),
         *               GenericInterface.getProperty(...), GenericInterface.setProperty(...)
         * @param args an array containing the bus interface name, member name, and input args for the
         *             request. For example, for a Method Call, these args would have the form
         *             {interfaceName, methodName, methodArgs...}, where methodArgs is an array of
         *             arguments passed into the bus method being called.
         *             The interface name and member name must correspond to a known MethodDef or
         *             PropertyDef.
         * @return the bus method/property call's return value
         * @throws IllegalArgumentException invalid proxy Method was specified.
         * @throws BusException if the proxy's interface definitions do not include a MethodDef or
         *             or PropertyDef corresponding to the given bus interface's method or property.
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws BusException {
            // Check that the called method is one of the accepted GenericInterface operations
            if (method.getDeclaringClass().equals(GenericInterface.class) && method.getName().equals("signal")) {
                throw new IllegalArgumentException(
                        "Invalid proxy method. Proxy does not support GenericInterface signal calls.");
            }

            boolean isGet = method.getName().equals("getProperty");
            boolean isSet = method.getName().equals("setProperty");
            boolean isMethod = method.getName().equals("methodCall");

            String interfaceName = null;
            String memberName = null;
            String outSig = null;
            Type outType = void.class;
            Object value = null;

            if (isMethod) {
                interfaceName = (String)getArg(args, 0, null);
                memberName = (String)getArg(args, 1, null);
                MethodDef methodDef = BusObjectInfo.getMethod(interfaceDefs,
                        interfaceName, memberName, null);
                if (methodDef == null) {
                    throw new BusException("No such method: " + interfaceName+"."+memberName);
                }

                outSig = methodDef.getReplySignature();
                outType = (outSig == null || outSig.isEmpty()) ? void.class : MsgArg.toType(outSig);
                Object[] methodArgs = (Object[])getArg(args, 2, new Object[]{});

                value = methodCall(bus,
                            methodDef.getInterfaceName(),
                            methodDef.getName(),
                            methodDef.getSignature(),
                            outType,
                            methodArgs,
                            replyTimeoutMsecs,
                            flags);
            } else if (isGet || isSet) {
                interfaceName = (String)getArg(args, 0, null);
                memberName = (String)getArg(args, 1, null);
                PropertyDef propertyDef = BusObjectInfo.getProperty(interfaceDefs,
                        interfaceName, memberName);
                if (propertyDef == null) {
                    throw new BusException("No such property: " + interfaceName+"."+memberName);
                }

                if (isGet) {
                    outSig = propertyDef.getType();
                    outType = MsgArg.toType(outSig);
                    Variant v = getProperty(bus,
                            propertyDef.getInterfaceName(),
                            propertyDef.getName());
                    value = v.getObject(outType);
                } else {
                    Object propertyArg = getArg(args, 2, null);
                    setProperty(bus,
                            propertyDef.getInterfaceName(),
                            propertyDef.getName(),
                            propertyDef.getType(),
                            propertyArg);
                }
            } else {
                try {
                    Class<Object> objectClass = Object.class;
                    if (method.equals(objectClass.getMethod("toString"))) {
                        return proxyToString(proxy,interfaceDefs);
                    }
                    if (method.equals(objectClass.getMethod("equals",objectClass))) {
                        return proxy == args[0];
                    }
                    if (method.equals(objectClass.getMethod("hashCode"))) {
                        return System.identityHashCode(proxy);
                    }
                } catch (NoSuchMethodException e) {
                    throw new BusException("No such method: " + method.getName());
                }
            }

            /*
             * The JNI layer can't perform complete type checking (at least not easily),
             * so this extra code is here. The conditions below are taken from the
             * InvocationHandler documentation.
             */
            boolean doThrow = false;
            Class<?> returnType = (Class<?>)outType;
            if (value == null) {
                doThrow = returnType.isPrimitive() && !returnType.isAssignableFrom(Void.TYPE);
            } else if (returnType.isPrimitive()) {
                if ((returnType.isAssignableFrom(Byte.TYPE) && !(value instanceof Byte))
                        || (returnType.isAssignableFrom(Short.TYPE) && !(value instanceof Short))
                        || (returnType.isAssignableFrom(Integer.TYPE) && !(value instanceof Integer))
                        || (returnType.isAssignableFrom(Long.TYPE) && !(value instanceof Long))
                        || (returnType.isAssignableFrom(Double.TYPE) && !(value instanceof Double))
                        || (returnType.isAssignableFrom(Boolean.TYPE) && !(value instanceof Boolean))) {
                    doThrow = true;
                }
            } else if (!returnType.isAssignableFrom(value.getClass())) {
                doThrow = true;
            }
            if (doThrow) {
                throw new MarshalBusException("cannot marshal '" + outSig + "' into " + returnType);
            }
            return value;
        }

        /** Return the indexed arg. If invalid index, return the given default value. */
        private Object getArg(Object[] args, int index, Object defaultValue) {
            if (index < 0) return defaultValue;
            return (args != null && args.length >= index+1) ? args[index] : defaultValue;
        }
    }

}
