package com.mendes15.satwithz3.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.z3.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SatSolver {

    private final ObjectMapper mapper = new ObjectMapper();

    public ArrayNode solve(JsonNode statements) {
        try (Context ctx = new Context()) {
            Solver solver = ctx.mkSolver();

            if (solver.check() == Status.SATISFIABLE) {

                return mapper.createArrayNode();
            } else {
                System.err.println("[Z3 Status] UNSATISFIABLE - Não há solução lógica consistente.");
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
            case "not"           -> ctx.mkNot(parseExpression(ctx, inhabitants, node.get(1)));
            case "and"           -> node.size() == 3 ?
                    ctx.mkAnd(parseExpression(ctx, inhabitants, node.get(1)), parseExpression(ctx, inhabitants, node.get(2))) :
                    ctx.mkAnd(parseRecursive(ctx, inhabitants, node));
            case "or"            -> node.size() == 3 ?
                    ctx.mkOr(parseExpression(ctx, inhabitants, node.get(1)), parseExpression(ctx, inhabitants, node.get(2))) :
                    ctx.mkOr(parseRecursive(ctx, inhabitants, node));
            case "->"            -> ctx.mkImplies(
                    parseExpression(ctx, inhabitants, node.get(1)),
                    parseExpression(ctx, inhabitants, node.get(2))
            );
            case "iff", "=="     -> ctx.mkEq(
                    parseExpression(ctx, inhabitants, node.get(1)),
                    parseExpression(ctx, inhabitants, node.get(2))
            );
            default -> ctx.mkTrue();
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