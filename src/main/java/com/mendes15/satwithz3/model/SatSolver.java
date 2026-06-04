package com.mendes15.satwithz3.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.z3.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SatSolver {
    public String solve(JsonNode statements, int totalInhabitants) {
        try (Context ctx = new Context()) {
            Solver solver = ctx.mkSolver();

            java.util.Map<String, BoolExpr> inhabitantsMap = new java.util.HashMap<>();

            for (int i = 0; i < totalInhabitants; i++) {
                String id = "H" + i;
                inhabitantsMap.put(id, ctx.mkBoolConst(id));
            }


            if (statements.isArray()) {
                for (JsonNode statementNode : statements) {
                    BoolExpr[] inhabitantsArray = inhabitantsMap.values().toArray(new BoolExpr[0]);
                    BoolExpr restriction = parseExpression(ctx, inhabitantsArray, statementNode);

                    if (restriction != null) {
                        solver.add(restriction);
                    }
                }
            } else {
                BoolExpr[] inhabitantsArray = inhabitantsMap.values().toArray(new BoolExpr[0]);
                BoolExpr restriction = parseExpression(ctx, inhabitantsArray, statements);
                if (restriction != null) {
                    solver.add(restriction);
                }
            }

            if (solver.check() == Status.SATISFIABLE) {
                Model model = solver.getModel();
                StringBuilder bufferLinha = new StringBuilder();

                for (int i = 0; i < totalInhabitants; i++) {
                    BoolExpr constHabitante = inhabitantsMap.get("H" + i);

                    Expr value = model.evaluate(constHabitante, true);

                    String letraValoracao = value.toString().equals("true") ? "t" : "f";
                    bufferLinha.append(letraValoracao);

                    if (i < totalInhabitants - 1) {
                        bufferLinha.append(",");
                    }
                }

                return bufferLinha.toString();
            } else {
                System.err.println("[Z3 Status] UNSATISFIABLE - Nenhuma atribuicao consistente encontrada.");
            }
        } catch (Exception e) {
            System.err.println("[Erro Z3 Parser interno]: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private BoolExpr parseExpression(Context ctx, BoolExpr[] inhabitants, JsonNode node) {
        if (node == null || node.isNull()) {
            return ctx.mkTrue();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? ctx.mkTrue() : ctx.mkFalse();
        }
        if (node.isTextual()) {
            return ctx.mkTrue();
        }
        if (!node.isArray() || node.isEmpty()) {
            return ctx.mkTrue();
        }

        String op = node.get(0).asText();

        return switch (op) {
            case "telling-truth" -> inhabitants[node.get(1).asInt()];

            case "lying"         -> ctx.mkNot(inhabitants[node.get(1).asInt()]);

            case "not", "negation" -> ctx.mkNot(parseExpression(ctx, inhabitants, node.get(1)));

            case "and"           -> node.size() == 3 ?
                    ctx.mkAnd(parseExpression(ctx, inhabitants, node.get(1)), parseExpression(ctx, inhabitants, node.get(2))) :
                    ctx.mkAnd(parseRecursive(ctx, inhabitants, node));

            case "or"            -> node.size() == 3 ?
                    ctx.mkOr(parseExpression(ctx, inhabitants, node.get(1)), parseExpression(ctx, inhabitants, node.get(2))) :
                    ctx.mkOr(parseRecursive(ctx, inhabitants, node));

            case "->", "implies" -> ctx.mkImplies(
                    parseExpression(ctx, inhabitants, node.get(1)),
                    parseExpression(ctx, inhabitants, node.get(2))
            );

            case "<=>", "iff", "==", "equivalent" -> ctx.mkEq(
                    parseExpression(ctx, inhabitants, node.get(1)),
                    parseExpression(ctx, inhabitants, node.get(2))
            );

            default -> {
                System.err.println("[AVISO PARSER] Operador desconhecido ignorado no dataset: " + op);
                yield ctx.mkTrue();
            }
        };
    }

    private BoolExpr[] parseRecursive(Context ctx, BoolExpr[] inhabitants, JsonNode node) {
        List<BoolExpr> exprs = new ArrayList<>();
        for (int i = 1; i < node.size(); i++) {
            exprs.add(parseExpression(ctx, inhabitants, node.get(i)));
        }
        return exprs.toArray(new BoolExpr[0]);
    }

}