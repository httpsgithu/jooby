/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby.internal.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.jooby.Context;

public class ReturnTypeParser {

  public static List<String> parse(ParserContext ctx, MethodNode node) {
    Type returnType = Type.getReturnType(node.desc);
    boolean notSynthetic = (node.access & Opcodes.ACC_SYNTHETIC) == 0;
    if (notSynthetic
        && !TypeFactory.OBJECT.equals(returnType)
        && !TypeFactory.VOID.equals(returnType)
        && !TypeFactory.JOOBY.equals(returnType)) {
      if (node.signature == null) {
        return Collections.singletonList(ASMType.parse(returnType.getDescriptor()));
      } else {
        String desc = node.signature;
        int rparen = desc.indexOf(')');
        if (rparen > 0) {
          desc = desc.substring(rparen + 1);
        }
        return Collections.singletonList(ASMType.parse(desc));
      }
    }
    return parseIgnoreSignature(ctx, node);
  }

  public static List<String> parseIgnoreSignature(ParserContext ctx, MethodNode node) {
    List<String> result = InsnSupport.next(node.instructions.getFirst())
        .filter(it -> it.getOpcode() == Opcodes.ARETURN || it.getOpcode() == Opcodes.IRETURN
            || it.getOpcode() == Opcodes.RETURN)
        .map(it -> handleReturnType(ctx, node, it))
        .map(Object::toString)
        .distinct()
        .collect(Collectors.toList());
    return result;
  }

  private static String handleReturnType(ParserContext ctx, MethodNode node, AbstractInsnNode it) {
    Type returnType = Type.getReturnType(node.desc);

    if (it.getOpcode() == Opcodes.RETURN) {
      return returnType.getClassName();
    }
    /** IRETURN */
    if (it.getOpcode() == Opcodes.IRETURN) {
      if (it instanceof InsnNode) {
        AbstractInsnNode prev = it.getPrevious();
        if (prev instanceof IntInsnNode) {
          return Integer.class.getName();
        }
        if (prev instanceof InsnNode) {
          if (prev.getOpcode() == Opcodes.ICONST_0
              || prev.getOpcode() == Opcodes.ICONST_1) {
            return Boolean.class.getName();
          }
        }
      }
    }

    for (Iterator<AbstractInsnNode> iterator = InsnSupport
        .prevIterator(it.getPrevious()); iterator.hasNext(); ) {
      AbstractInsnNode i = iterator.next();
      if (i instanceof MethodInsnNode && (((MethodInsnNode) i).owner
          .equals("kotlin/jvm/internal/Intrinsics"))) {
        // skip Ldc and load var
        // dup or aload
        // visitLdcInsn("$receiver");
        // visitMethodInsn(INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkParameterIsNotNull", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
        iterator.next();
        iterator.next();
        continue;
      }
      if (i instanceof MethodInsnNode && (((MethodInsnNode) i).owner
          .equals("kotlin/TypeCastException"))) {
        continue;
      }
      if (i instanceof LineNumberNode || i instanceof LabelNode) {
        continue;
      }
      /** return 1; return true; return new Foo(); */
      if (i instanceof MethodInsnNode) {
        MethodInsnNode minnsn = (MethodInsnNode) i;
        if (minnsn.name.equals("<init>")) {
          return Type.getObjectType(minnsn.owner).getClassName();
        } else {
          return fromMethodCall(ctx, minnsn);
        }
      }
      /** return "String" | int | double */
      if (i instanceof LdcInsnNode) {
        Object cst = ((LdcInsnNode) i).cst;
        if (cst instanceof Type) {
          return ((Type) cst).getClassName();
        }
        return cst.getClass().getName();
      }

      /** return variable */
      if (i instanceof VarInsnNode) {
        VarInsnNode varInsn = (VarInsnNode) i;
        String varType = localVariable(ctx, node, varInsn);
        // Is there local variable?
        if (varType != null) {
          return varType;
        }
      }
      /** Invoke dynamic: */
      if (i instanceof InvokeDynamicInsnNode) {
        InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) i;
        String handleDescriptor = Stream.of(invokeDynamic.bsmArgs)
            .filter(Handle.class::isInstance)
            .map(Handle.class::cast)
            .findFirst()
            .map(h -> {
              String desc = Type.getReturnType(h.getDesc()).getDescriptor();
              return "V".equals(desc) ? "java/lang/Object" : desc;
            })
            .orElse(null);
        String descriptor = Type
            .getReturnType(invokeDynamic.desc)
            .getDescriptor();
        if (handleDescriptor != null && !handleDescriptor.equals("java/lang/Object")) {
          if (descriptor.endsWith(";")) {
            descriptor = descriptor.substring(0, descriptor.length() - 1);
          }
          descriptor += "<" + handleDescriptor + ">;";
        }
        return ASMType.parse(descriptor);
      }
      /** array literal: */
      if (i.getOpcode() == Opcodes.NEWARRAY) {
        // empty primitive array
        if (i instanceof IntInsnNode) {
          switch (((IntInsnNode) i).operand) {
            case Opcodes.T_BOOLEAN:
              return boolean[].class.getName();
            case Opcodes.T_CHAR:
              return char[].class.getName();
            case Opcodes.T_BYTE:
              return byte[].class.getName();
            case Opcodes.T_SHORT:
              return short[].class.getName();
            case Opcodes.T_INT:
              return int[].class.getName();
            case Opcodes.T_LONG:
              return long[].class.getName();
            case Opcodes.T_FLOAT:
              return float[].class.getName();
            case Opcodes.T_DOUBLE:
              return double[].class.getName();
          }
        }
      }
      // empty array of objects
      if (i.getOpcode() == Opcodes.ANEWARRAY) {
        TypeInsnNode typeInsn = (TypeInsnNode) i;
        return ASMType.parse("[L" + typeInsn.desc + ";");
      }
      // non empty array
      switch (i.getOpcode()) {
        case Opcodes.BASTORE:
          return boolean[].class.getName();
        case Opcodes.CASTORE:
          return char[].class.getName();
        case Opcodes.SASTORE:
          return short[].class.getName();
        case Opcodes.IASTORE:
          return int[].class.getName();
        case Opcodes.LASTORE:
          return long[].class.getName();
        case Opcodes.FASTORE:
          return float[].class.getName();
        case Opcodes.DASTORE:
          return double[].class.getName();
        case Opcodes.AASTORE:
          return InsnSupport.prev(i)
              .filter(e -> e.getOpcode() == Opcodes.ANEWARRAY)
              .findFirst()
              .map(e -> {
                TypeInsnNode typeInsn = (TypeInsnNode) e;
                return ASMType.parse("[L" + typeInsn.desc + ";");
              })
              .orElse(Object.class.getName());
      }
    }

    return returnType.getClassName();
  }

  private static String fromMethodCall(ParserContext ctx, MethodInsnNode node) {
    if (node.owner.equals(TypeFactory.CONTEXT.getInternalName())) {
      // handle: return ctx.body(MyType.class)
      Type[] arguments = Type.getArgumentTypes(node.desc);
      if (arguments.length == 1 && arguments[0].getClassName().equals(Class.class.getName())) {
        return InsnSupport.prev(node)
            .filter(LdcInsnNode.class::isInstance)
            .findFirst()
            .map(LdcInsnNode.class::cast)
            .filter(it -> it.cst instanceof Type)
            .map(it -> (Type) it.cst)
            .orElse(TypeFactory.OBJECT)
            .getClassName()
            ;
      }
      return Object.class.getName();
    }
    Type returnType = Type.getReturnType(node.desc);
    // Since Kotlin 1.6+
    String methodName = node.name.startsWith("access$invoke$")
        ? node.name.substring("access$".length())
        : node.name;
    List<MethodNode> methodNodes = classMethods(ctx, node.owner);
    return methodNodes.stream()
        .filter(m -> m.name.equals(methodName) && m.desc.equals(node.desc))
        .findFirst()
        .map(m -> Optional.ofNullable(m.signature)
            .map(s -> {
              int pos = s.indexOf(')');
              return pos > 0 ? s.substring(pos + 1) : s;
            })
            .map(ASMType::parse)
            .orElseGet(() -> Type.getReturnType(m.desc).getClassName())
        )
        .orElse(returnType.getClassName());
  }

  private static List<MethodNode> classMethods(ParserContext ctx, String owner) {
    ClassNode classNode = ctx.classNodeOrNull(Type.getObjectType(owner));
    if (classNode == null) {
      return Collections.emptyList();
    }
    List<MethodNode> result = new ArrayList<>();
    result.addAll(classNode.methods);
    if (classNode.interfaces != null) {
      for (String anInterface : classNode.interfaces) {
        result.addAll(classMethods(ctx, anInterface));
      }
    }
    return result;
  }

  private static String localVariable(final ParserContext ctx, final MethodNode m,
      final VarInsnNode varInsn) {
    int opcode = varInsn.getOpcode();
    if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ISTORE) {
      List<LocalVariableNode> vars = m.localVariables;
      LocalVariableNode var = vars.stream()
          .filter(v -> v.index == varInsn.var)
          .findFirst()
          .orElse(null);
      if (var != null) {
        if (var.signature == null) {
          /** Kotlin traversal: */
          Optional<AbstractInsnNode> kt = InsnSupport.prev(varInsn).filter(kotlinIntrinsics())
              .findFirst();
          if (kt.isPresent()) {
            LocalVariableNode $this = vars.stream().filter(v -> v.name.equals("this"))
                .findFirst()
                .orElse(null);
            if ($this != null) {
              Type kotlinLambda = Type.getType($this.desc);
              ClassNode classNode = ctx.classNodeOrNull(kotlinLambda);
              if (classNode != null && classNode.signature != null) {
                String type = ASMType.parse(classNode.signature, internalName ->
                    !internalName.equals("kotlin/jvm/internal/Lambda")
                        && !internalName.equals("kotlin/jvm/functions/Function1")
                        && !internalName.equals("io/jooby/HandlerContext")
                );
                if (!type.equals(Object.class.getName()) && !type.equals(void.class.getName())) {
                  return type;
                }
              }
            }
          }

          String type = ASMType.parse(var.desc);
          if (type.startsWith("java.util.")) {
            /** Try to find originating call to figure out element type <T> */
            VarInsnNode astore = InsnSupport.prev(varInsn)
                .filter(VarInsnNode.class::isInstance)
                .map(VarInsnNode.class::cast)
                .filter(varIns -> varIns.getOpcode() == Opcodes.ASTORE && varIns.var == var.index)
                .findFirst()
                .orElse(null);
            if (astore != null) {
              MethodInsnNode methodCall = InsnSupport.prev(astore)
                  .filter(it -> (it instanceof MethodInsnNode) && !kotlinIntrinsics().test(it))
                  .map(MethodInsnNode.class::cast)
                  .findFirst()
                  .orElse(null);
              if (methodCall != null) {
                String returnType = fromMethodCall(ctx, methodCall);
                if (!returnType.equals(Object.class.getName()) && !returnType
                    .equals(void.class.getName())) {
                  type = returnType;
                }
              }
            }
          }
          if (type.equals(Context.class.getName())) {
            // No var, look for last STORE matching index var
            VarInsnNode store = InsnSupport.prev(varInsn.getPrevious())
                .filter(
                    it -> (it.getOpcode() >= Opcodes.ISTORE && it.getOpcode() <= Opcodes.SASTORE))
                .filter(VarInsnNode.class::isInstance)
                .map(VarInsnNode.class::cast)
                .filter(it -> it.var == varInsn.var)
                .findFirst()
                .orElse(null);
            if (store != null) {
              type = handleReturnType(ctx, m, store);
            }
          }
          return type;
        }
        return ASMType.parse(var.signature);
      }
    }
    return null;//Object.class.getName();
  }

  private static Predicate<AbstractInsnNode> kotlinIntrinsics() {
    return i -> (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner
        .equals("kotlin/jvm/internal/Intrinsics"));
  }
}
