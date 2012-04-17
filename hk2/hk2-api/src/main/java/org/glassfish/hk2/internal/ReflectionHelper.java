/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.hk2.internal;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Qualifier;
import javax.inject.Scope;

import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Contract;

/**
 * @author jwells
 *
 */
public class ReflectionHelper {
    private final static HashSet<Character> ESCAPE_CHARACTERS = new HashSet<Character>();
    private final static char ILLEGAL_CHARACTERS[] = {
        '{' , '}', '[', ']', ':', ';', '=', ',', '\\'
    };
    private final static HashMap<Character, Character> REPLACE_CHARACTERS = new HashMap<Character, Character>();
    
    static {
        for (char illegal : ILLEGAL_CHARACTERS) {
            ESCAPE_CHARACTERS.add(illegal);
        }
        
        REPLACE_CHARACTERS.put('\n', 'n');
        REPLACE_CHARACTERS.put('\r', 'r');
    }
    
    /**
     * Given the type parameter gets the raw type represented
     * by the type, or null if this has no associated raw class
     * @param type The type to find the raw class on
     * @return The raw class associated with this type
     */
    public static Class<?> getRawClass(Type type) {
        if (type == null) return null;
        
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }
        
        return null;
    }
    
    private static String getNamedName(Named named, Class<?> implClass) {
        String name = named.value();
        if (name != null && !name.equals("")) return name;
        
        String cn = implClass.getName();
            
        int index = cn.lastIndexOf(".");
        if (index < 0) return cn;
        
        return cn.substring(index + 1);
    }
    
    /**
     * Returns the name that should be associated with this class
     * 
     * @param implClass The class to evaluate
     * @return The name this class should have
     */
    public static String getName(Class<?> implClass) {
        Named named = implClass.getAnnotation(Named.class);
        
        String namedName = (named != null) ? getNamedName(named, implClass) : null ;
        
        if (namedName != null) return namedName;
        
        return null;
    }
    
    /**
     * Returns the set of types this class advertises
     * @param t the object we are analyzing
     * @return The type itself and the contracts it implements
     */
    public static Set<Type> getAdvertisedTypesFromObject(Object t) {
        Set<Type> retVal = new LinkedHashSet<Type>();
        if (t == null) return retVal;
        
        retVal.add(t.getClass());
        
        Type genericInterfaces[] = t.getClass().getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            Class<?> rawClass = getRawClass(genericInterface);
            if (rawClass == null) continue;
            
            if (rawClass.isAnnotationPresent(Contract.class)) {
                retVal.add(genericInterface);
            }
        }
        
        return retVal;
    }
    
    /**
     * Returns the set of types this class advertises
     * @param clazz the class we are analyzing
     * @return The type itself and the contracts it implements
     */
    public static Set<String> getContractsFromClass(Class<?> clazz) {
        Set<String> retVal = new LinkedHashSet<String>();
        if (clazz == null) return retVal;
        
        retVal.add(clazz.getName());
        
        Class<?> interfaces[] = clazz.getInterfaces();
        for (Class<?> iFace : interfaces) {
            if (iFace.isAnnotationPresent(Contract.class)) {
                retVal.add(iFace.getName());
            }
        }
        
        return retVal;
    }
    
    /**
     * Gets the scope annotation from the object
     * @param t The object to analyze
     * @return The class of the scope annotation
     */
    public static Class<? extends Annotation> getScopeFromObject(Object t) {
        if (t == null) return PerLookup.class;
        
        return getScopeFromClass(t.getClass());
    }
    
    /**
     * Gets the scope annotation from the object
     * @param clazz The class to analyze
     * @return The class of the scope annotation
     */
    public static Class<? extends Annotation> getScopeFromClass(Class<?> clazz) {
        if (clazz == null) return PerLookup.class;
        
        for (Annotation annotation : clazz.getAnnotations()) {
            Class<? extends Annotation> annoClass = annotation.annotationType();
            
            if (annoClass.isAnnotationPresent(Scope.class)) {
                return annoClass;
            }
            
        }
        
        return PerLookup.class;
    }
    
    /**
     * Gets all the qualifiers from the object
     * 
     * @param t The object to analyze
     * @return The set of qualifiers.  Will not return null but may return an empty set
     */
    public static Set<Annotation> getQualifiersFromObject(Object t) {
        Set<Annotation> retVal = new LinkedHashSet<Annotation>();
        if (t == null) return retVal;
        
        Class<?> oClass = t.getClass();
        for (Annotation annotation : oClass.getAnnotations()) {
            Class<? extends Annotation> annoClass = annotation.annotationType();
            
            if (annoClass.isAnnotationPresent(Qualifier.class)) {
                retVal.add(annotation);
            }
            
        }
        
        return retVal;
    }
    
    /**
     * Gets all the qualifiers from the object
     * 
     * @param clazz The class to analyze
     * @return The set of qualifiers.  Will not return null but may return an empty set
     */
    public static Set<String> getQualifiersFromClass(Class<?> clazz) {
        Set<String> retVal = new LinkedHashSet<String>();
        if (clazz == null) return retVal;
        
        for (Annotation annotation : clazz.getAnnotations()) {
            Class<? extends Annotation> annoClass = annotation.annotationType();
            
            if (annoClass.isAnnotationPresent(Qualifier.class)) {
                retVal.add(annotation.annotationType().getName());
            }
            
        }
        
        return retVal;
    }
    
    /**
     * Writes a set in a way that can be read from an input stream as well
     * 
     * @param set The set to write
     * @return a representation of a list
     */
    public static String writeSet(Set<?> set) {
        if (set == null) return "{}";
        
        StringBuffer sb = new StringBuffer("{");
        
        boolean first = true;
        for (Object writeMe : set) {
            if (first) {
                first = false;
                sb.append(escapeString(writeMe.toString()));
            }
            else {
                sb.append("," + escapeString(writeMe.toString()));
            }
        }
        
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * Writes a set in a way that can be read from an input stream as well.  The values in
     * the set may not contain the characters "{},"
     * 
     * @param line The line to read
     * @param addToMe The set to add the strings to
     * @throws IOException On a failure
     */
    public static void readSet(String line, Collection<String> addToMe) throws IOException {
        char asChars[] = new char[line.length()];
        line.getChars(0, line.length(), asChars, 0);
        
        internalReadSet(asChars, 0, addToMe);
    }
    
    /**
     * Writes a set in a way that can be read from an input stream as well.  The values in
     * the set may not contain the characters "{},"
     * 
     * @param line The line to read
     * @param addToMe The set to add the strings to
     * @return The number of characters read until the end of the set
     * @throws IOException On a failure
     */
    private static int internalReadSet(char asChars[], int startIndex, Collection<String> addToMe) throws IOException {
        int dot = startIndex;
        int startOfSet = -1;
        while (dot < asChars.length) {
            if (asChars[dot] == '{') {
                startOfSet = dot;
                dot++;
                break;
            }
            dot++;
        }
        
        if (startOfSet == -1) {
            throw new IOException("Unknown set format, no initial { character : " + new String(asChars));
        }
        
        StringBuffer elementBuffer = new StringBuffer();
        int endOfSet = -1;
        while (dot < asChars.length) {
            char dotChar = asChars[dot];
            
            if (dotChar == '}') {
                addToMe.add(elementBuffer.toString());
                
                endOfSet = dot;
                break;  // Done!
            }
            
            if (dotChar == ',') {
                // Terminating a single element
                addToMe.add(elementBuffer.toString());
                
                elementBuffer = new StringBuffer();
            }
            else {
                // This character is either an escape character or a real character
                if (dotChar != '\\') {
                    elementBuffer.append(dotChar);
                }
                else {
                    // This is an escape character
                    if (dot + 1 >= asChars.length) {
                        // This is an error, escape at end of buffer
                        break;
                    }
                    
                    dot++;  // Moves it forward
                    dotChar = asChars[dot];
                    
                    if (dotChar == 'n') {
                        elementBuffer.append('\n');
                    }
                    else if (dotChar == 'r') {
                        elementBuffer.append('\r');
                    }
                    else {
                        elementBuffer.append(dotChar);
                    }
                }
                
            }
            
            dot++;
        }
        
        if (endOfSet == -1) {
            throw new IOException("Unknown set format, no ending } character : " + new String(asChars));
        }
        
        return dot - startIndex;
    }
    
    private static int readKeyStringListLine(char asChars[], int startIndex, Map<String, List<String>> addToMe) throws IOException {
        int dot = startIndex;
        
        int equalsIndex = -1;
        while (dot < asChars.length) {
            char dotChar = asChars[dot];
            
            if (dotChar == '=') {
                equalsIndex = dot;
                break;
            }
            
            dot++;
        }
        
        if (equalsIndex < 0) {
            throw new IOException("Uknown key-string list format, no equals: " + new String(asChars));
        }
        
        String key = new String(asChars, startIndex, (equalsIndex - startIndex));  // Does not include the =
        dot++;  // Move it past the equals
        
        if (dot >= asChars.length) {
            // Key with no values, this is illegal
            throw new IOException("Found a key with no value, " + key + " in line " + new String(asChars));
            
        }
        
        LinkedList<String> listValues = new LinkedList<String>();
        
        int addOn = internalReadSet(asChars, dot, listValues);
        if (!listValues.isEmpty()) {
            addToMe.put(key, listValues);
        }
        
        dot += addOn + 1;
        if (dot < asChars.length) {
            char skipComma = asChars[dot];
            if (skipComma == ',') {
                dot++;
            }
        }
        
        return dot - startIndex;  // The +1 gets us to the next character in the stream
    }
    
    /**
     * Writes a set in a way that can be read from an input stream as well
     * @param line The line to read
     * @param addToMe The set to add the strings to
     * @throws IOException On a failure
     */
    public static void readMetadataMap(String line, Map<String, List<String>> addToMe) throws IOException {
        char asChars[] = new char[line.length()];
        line.getChars(0, line.length(), asChars, 0);
        
        int dot = 0;
        while (dot < asChars.length) {
            int addMe = readKeyStringListLine(asChars, dot, addToMe);
            dot += addMe;
        }
    }
    
    private static String escapeString(String escapeMe) {
        char asChars[] = new char[escapeMe.length()];
        
        escapeMe.getChars(0, escapeMe.length(), asChars, 0);
        
        StringBuffer sb = new StringBuffer();
        for (int lcv = 0; lcv < asChars.length; lcv++) {
            char candidateChar = asChars[lcv];
            
            if (ESCAPE_CHARACTERS.contains(candidateChar)) {
                sb.append('\\');
                sb.append(candidateChar);
            }
            else if (REPLACE_CHARACTERS.containsKey(candidateChar)) {
                char replaceWithMe = REPLACE_CHARACTERS.get(candidateChar);
                sb.append('\\');
                sb.append(replaceWithMe);
            }
            else {
                sb.append(candidateChar);
            }
        }
        
        return sb.toString();
    }
    
    private static String writeList(List<String> list) {
        StringBuffer sb = new StringBuffer("{");
        
        boolean first = true;
        for (String writeMe : list) {
            if (first) {
                first = false;
                sb.append(escapeString(writeMe.toString()));
            }
            else {
                sb.append("," + escapeString(writeMe.toString()));
            }
        }
        
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * Used to write the metadata out
     * 
     * @param metadata The metadata to externalize
     * @return The metadata in an externalizable format
     */
    public static String writeMetadata(Map<String, List<String>> metadata) {
        StringBuffer sb = new StringBuffer();
        
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : metadata.entrySet()) {
            if (first) {
                first = false;
                sb.append(entry.getKey() + '=');
            }
            else {
                sb.append("," + entry.getKey() + '=');
            }
            
            sb.append(writeList(entry.getValue()));
        }
        
        return sb.toString();
    }
    
    /**
     * Pretty prints this descriptor
     * 
     * @param d The descriptor to write out nicely
     * @return The descriptor to print
     */
    public static String prettyPrintDescriptor(Descriptor d) {
        StringBuffer sb = new StringBuffer("Descriptor(");
        
        sb.append("\n\timplementation=" + d.getImplementation());
        
        if (d.getName() != null) {
            sb.append("\n\tname=" + d.getName());
        }
        
        sb.append("\n\tcontracts=");
        sb.append(writeSet(d.getAdvertisedContracts()));
        
        sb.append("\n\tscope=" + d.getScope());
        
        sb.append("\n\tqualifiers=");
        sb.append(writeSet(d.getQualifiers()));
        
        sb.append("\n\tdescriptorType=" + d.getDescriptorType());
        
        sb.append("\n\tmetadata=");
        sb.append(writeMetadata(d.getMetadata()));
        
        sb.append("\n\tloader=" + d.getLoader());
        
        sb.append("\n\tid=" + d.getServiceId());
        
        sb.append("\n\tlocatorId=" + d.getServiceId());
        
        sb.append("\n\tidentityHashCode=" + System.identityHashCode(d));
        
        sb.append(")");
        
        return sb.toString();
    }

    /**
     * Adds a value to the list of values associated with this key
     * 
     * @param metadatas The base metadata object
     * @param key The key to which to add the value.  May not be null
     * @param value The value to add.  May not be null
     */
    public static void addMetadata(Map<String, List<String>> metadatas, String key, String value) {
        if (key == null || value == null) return;
        if (key.indexOf('=') >= 0) {
            throw new IllegalArgumentException("The key field may not have an = in it:" + key);
        }
        
        List<String> inner = metadatas.get(key);
        if (inner == null) {
            inner = new LinkedList<String>();
            metadatas.put(key, inner);
        }
        
        inner.add(value);
    }
    
    /**
     * Removes the given value from the given key
     * 
     * @param metadatas The base metadata object
     * @param key The key of the value to remove.  May not be null
     * @param value The value to remove.  May not be null
     * @return true if the value was removed
     */
    public static boolean removeMetadata(Map<String, List<String>> metadatas, String key, String value) {
        if (key == null || value == null) return false;
        
        List<String> inner = metadatas.get(key);
        if (inner == null) return false;
        
        boolean retVal = inner.remove(value);
        if (inner.size() <= 0) metadatas.remove(key);
        
        return retVal;
    }
    
    /**
     * Removes all the metadata values associated with key
     * 
     * @param metadatas The base metadata object
     * @param key The key of the metadata values to remove
     * @return true if any value was removed
     */
    public static boolean removeAllMetadata(Map<String, List<String>> metadatas, String key) {
        List<String> values = metadatas.remove(key);
        return (values != null && values.size() > 0);
    }
    
    /**
     * This method does a deep copy of the incoming meta-data, (which basically means we will
     * also make copies of the value list)
     * 
     * @param copyMe The guy to copy (if null, null will be returned)
     * @return A deep copy of the metadata
     */
    public static Map<String, List<String>> deepCopyMetadata(Map<String, List<String>> copyMe) {
        if (copyMe == null) return null;
        
        Map<String, List<String>> retVal = new LinkedHashMap<String, List<String>>();
        
        for (Map.Entry<String, List<String>> entry : copyMe.entrySet()) {
            String key = entry.getKey();
            if (key.indexOf('=') >= 0) {
                throw new IllegalArgumentException("The key field may not have an = in it:" + key);
            }
            
            List<String> values = entry.getValue();
            LinkedList<String> valuesCopy = new LinkedList<String>();
            for (String value : values) {
                valuesCopy.add(value);
            }
            
            retVal.put(key, valuesCopy);
        }
        
        return retVal;
    }
}
