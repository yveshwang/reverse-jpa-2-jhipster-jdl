package io.github.jhipster.jpa2jdl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.Set;

public class ReverseJPA2JDL {
    private static final Logger LOG = LoggerFactory.getLogger(ReverseJPA2JDL.class);
    public String generate(Set<Class<?>> entitySubClasses, Set<Class<?>> enums ) {
        final StringBuilder jdl = new StringBuilder();
        for(Class<?> e : enums) {
            generateEnum2Jdl(jdl, e);
        }
        StringBuilder relationShips = new StringBuilder();
        for(Class<?> e : entitySubClasses) {
            generateClass2Jdl(jdl, relationShips, e);
        }
        jdl.append(relationShips);
        for(Class<?> e : entitySubClasses) {
            generatePagination(jdl, e);
        }
        for(Class<?> e : entitySubClasses) {
            generateDto(jdl, e);
        }
        for(Class<?> e : entitySubClasses) {
            generateServices(jdl, e);
        }
        return jdl.toString();
    }
    private void genreateOption(final String prefix, final String suffix, final StringBuilder out, final Class<?> e) {
        final String entityClassName = e.getSimpleName();
        out.append(prefix + " " + entityClassName + " " + suffix + "\n");
    }
    public void generatePagination(final StringBuilder out, final Class<?> e) {
        genreateOption("paginate", "with pager", out, e);
    }
    public void generateServices(final StringBuilder out, final Class<?> e) {
        genreateOption("service", "with serviceClass", out, e);
    }
    public void generateDto(final StringBuilder out, final Class<?> e) {
        genreateOption("dto", "with mapstruct", out, e);
    }
    public void generateEnum2Jdl(final StringBuilder out, final Class<?> e) {
        final String entityClassName = e.getSimpleName();
        boolean firstField = true;
        out.append("enum " + entityClassName + " {\n");
        final Field[] declaredFields = e.getDeclaredFields();
        for(final Field f : declaredFields) {
            final String fieldName = f.getName();
            if (f.isSynthetic() || !Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers())) {
                continue;
            }
            if (firstField) {
                firstField = false;
            } else {
                out.append(",\n");
            }
            out.append("  " + fieldName);
        }
        out.append("\n");
        out.append("}\n\n");
    }

    public void generateClass2Jdl(StringBuilder out, StringBuilder relationShips,  Class<?> e) {
        final String entityClassName = e.getSimpleName();
        boolean firstField = true;
        out.append("entity " + entityClassName + " {\n"); // inheritance NOT SUPPORTED YET in JDL ???
        final Field[] declaredFields = e.getDeclaredFields();
        for(final Field f : declaredFields) {
            String fieldName = f.getName();
            Type fieldType = f.getType();
            if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            // Annotation[] fieldAnnotations = f.getDeclaredAnnotations();

            if (f.getDeclaredAnnotation(Transient.class) != null) {
                continue;
            }

            String relationType = null;
            Class<?> targetEntityClass = null;
            boolean fromMany = false;
            boolean toMany = false;
            String mappedBy = "";
            final OneToMany oneToManyAnnotation = f.getDeclaredAnnotation(OneToMany.class);
            if (oneToManyAnnotation != null) {
                relationType = "OneToMany";
                targetEntityClass = oneToManyAnnotation.targetEntity();
                fromMany = false;
                toMany = true;
                mappedBy = oneToManyAnnotation.mappedBy();
            }
            final OneToOne oneToOneAnnotation = f.getDeclaredAnnotation(OneToOne.class);
            if (oneToOneAnnotation != null) {
                relationType = "OneToOne";
                targetEntityClass = oneToOneAnnotation.targetEntity();
                fromMany = false;
                toMany = false;
                mappedBy = oneToOneAnnotation.mappedBy();
            }
            final ManyToMany manyToManyAnnotation = f.getDeclaredAnnotation(ManyToMany.class);
            if (manyToManyAnnotation != null) {
                relationType = "ManyToMany";
                targetEntityClass = manyToManyAnnotation.targetEntity();
                fromMany = true;
                toMany = true;
                mappedBy = manyToManyAnnotation.mappedBy();
            }
            final ManyToOne manyToOneAnnotation = f.getDeclaredAnnotation(ManyToOne.class);
            if (manyToOneAnnotation != null) {
                relationType = "ManyToOne";
                targetEntityClass = manyToOneAnnotation.targetEntity();
                fromMany = true;
                toMany = false;
            }

            if (relationType != null) {
                // relationship
                relationShips.append("relationship " + relationType + " {\n");
                if (targetEntityClass == void.class || targetEntityClass == null) {
                    targetEntityClass = typeToClass(fieldType);
                }

                if (toMany && targetEntityClass != null) {
                    if (Collection.class.isAssignableFrom(targetEntityClass)) {
                        final Class<?> compType = targetEntityClass.getComponentType();
                        if (compType != null) {
                            targetEntityClass = compType;
                        } else {
                            final Type fieldGenericType = f.getGenericType();
                            if (fieldGenericType instanceof ParameterizedType) {
                                final ParameterizedType pt = (ParameterizedType) fieldGenericType;
                                targetEntityClass = typeToClass(pt.getActualTypeArguments()[0]);
                            }
                        }
                    }
                }

                final String targetEntityClassName = targetEntityClass != null? targetEntityClass.getSimpleName() : "";
                if (fromMany && toMany) {
                    LOG.info("ManyToMany .. mappedBy ??");
                }
                relationShips.append("  " + entityClassName + "{" + fieldName);
                if (mappedBy != null && !"".equals(mappedBy)) {
                    relationShips.append("(" + mappedBy + ")");
                }
                relationShips.append("} to " + targetEntityClassName + "\n");
                relationShips.append("}\n\n");
            } else {
                // simple field
                if (firstField) {
                    firstField = false;
                } else {
                    out.append(",\n");
                }
                out.append("  " + fieldName + " " + f.getType().getSimpleName());
            }
        }
        out.append("\n");
        out.append("}\n\n");
    }

    private static Class<?> typeToClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return typeToClass(((ParameterizedType) type).getRawType());
        } else if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Class<?> componentClass = typeToClass(componentType);
            if (componentClass != null) {
                return Array.newInstance(componentClass, 0).getClass();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}