import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;
import java.util.regex.Pattern;

public class TrialFeaturizing {


    static final class paramSetting {
//        LinkedList<Object> stack = new LinkedList<>();
        LinkedList<Object> stack;
//        LinkedList<Object> head = new LinkedList<>();
        LinkedList<Object> head;
        LinkedList<Object> head_rem = new LinkedList<>();
        Object current = new Object();
//        LinkedList<Object> tail = new LinkedList<>();
        LinkedList<Object> tail;

        LinkedList<Integer> counter = new LinkedList<>();
        LinkedList<Integer> contentLengthList = new LinkedList<>();

        LinkedList<Object> parents = new LinkedList<>();
        LinkedList<List<Object>> siblings = new LinkedList<>();
        LinkedList<String> var_cache = new LinkedList<>();
        Map<String, List<List<Object>>> var_siblings = new LinkedHashMap<>();

        ArrayList<String> feat_self = new ArrayList<>();
        ArrayList<String> feat_parents = new ArrayList<>();
        ArrayList<String> feat_siblings = new ArrayList<>();
        ArrayList<String> feat_var_siblings = new ArrayList<>();


        List<String> all_feat = new ArrayList<>();
        Boolean is_var = false;
        String globalVarName;
        Map<String, List<Integer>> vocab = new HashMap<>();
        ArrayList<Integer> featList = new ArrayList<>();

        int n_parents = 3;
        int n_siblings = 1;
        int n_var_siblings = 2;

        int n_reserved = 10;
        int leaf_idx = 0;
    }

    paramSetting params = new paramSetting();

    public Object iterationAST(JSONArray ast, String returnType) {

//        List<Object> data = JSONArray.parseArray(ast, Object.class);
//        params.stack = new LinkedList<>(JSONArray.parseArray(ast, Object.class));
        params.stack = new LinkedList<>(ast);
        while (params.stack.size() > 0) {
            boolean isArray = false;
            if (params.stack.getFirst() instanceof JSONArray) {
                params.head = new LinkedList<>();
                params.head.addAll((JSONArray) params.stack.getFirst());
                isArray = true;
            } else {
                params.head = new LinkedList<>();
                params.head.push(params.stack.getFirst());
            }

            params.tail = new LinkedList<>(params.stack);
            params.tail.removeFirst();

            params.current = params.head.getFirst();
            params.is_var = false;

            if (params.current instanceof JSONObject) {
                JSONObject currentDict = (JSONObject) params.current;
                if (currentDict.containsKey("f") && currentDict.getBoolean("f")) {

                    params.leaf_idx += 1;
                    params.globalVarName = currentDict.getString("t");
                    if (params.var_cache.size() != 0) {
                        List<List<Object>> correspondingContext = new LinkedList<>(params.var_siblings.get(params.var_cache.getFirst()));
                        List<List<Object>> selectedVarSiblings = correspondingContext.subList(Math.max(correspondingContext.size() - params.n_var_siblings, 0), correspondingContext.size());
                        for (List<Object> selectedVarSibling : selectedVarSiblings) {
                            String feat = selectedVarSibling.get(1) + ">>>" + params.globalVarName;
                            params.feat_var_siblings.add(feat);
                        }
                        List<Object> sublist = new ArrayList<>();
                        sublist.add(params.leaf_idx - 1);
                        sublist.add(params.globalVarName);
                        correspondingContext.add(sublist);
                        params.var_siblings.put(params.var_cache.getFirst(), correspondingContext);
                        params.var_cache.pop();
                    }

                    if (currentDict.containsKey("v") && !Character.isUpperCase(params.globalVarName.charAt(0))) {
                        params.is_var = true;
                    }
                }
            }

            if (isArray) {
                maintainingParents(params.head);
            } else {
                maintainingParents(params.stack);
            }

            maintainingSiblings();
            maintainingVarSiblings();

            params.head_rem = params.head;
            params.head_rem.removeFirst();

            params.stack = params.head_rem;
            params.stack.addAll(params.tail);


        }

        params.all_feat.addAll(params.feat_self);
        params.all_feat.addAll(params.feat_parents);
        params.all_feat.addAll(params.feat_siblings);
        params.all_feat.addAll(params.feat_var_siblings);

        vocabBuilder();
        if (returnType.equals("vocab")) {
            return params.vocab;
        } else {
            return params.all_feat;
        }

    }


    public void maintainingParents(LinkedList<Object> content) {
        if (params.parents.size() != 0) {
            while (params.counter.getFirst() == 0) {
                params.counter.pop();
                params.parents.pop();
                params.contentLengthList.pop();
            }
        }

        if (params.current instanceof String) {
            int contentLength = content.size();
            params.parents.push(params.current);
            if (params.counter.size() != 0) {
                int lastIndex = params.counter.getFirst() - 1;
                params.counter.pop();
                params.counter.push(lastIndex);
            }
            params.counter.push(contentLength);
            params.contentLengthList.push(contentLength);
        }

        int lastIndex = params.counter.getFirst() - 1;
        params.counter.pop();
        params.counter.push(lastIndex);

         if (params.current instanceof JSONObject) {
            JSONObject currentDict = (JSONObject) params.current;
            if (currentDict.containsKey("f") && currentDict.getBoolean("f")) {
                if (currentDict.containsKey("v") && !Character.isUpperCase(((String) currentDict.get("t")).charAt(0))) {
                    currentDict.put("t", "#VAR");
                }
                String self = currentDict.getString("t");
                params.feat_self.add(self);

                LinkedList<Integer> subleafIndex = new LinkedList<>();
                Iterator<Integer> it1 = params.contentLengthList.iterator();
                Iterator<Integer> it2 = params.counter.iterator();
                while (it1.hasNext() && it2.hasNext()) {
                    subleafIndex.add(it1.next() - it2.next());
                }

                LinkedList<List<Object>> parentIndexPair = new LinkedList<>();
                Iterator<Object> it3 = params.parents.iterator();
                Iterator<Integer> it4 = subleafIndex.iterator();
                while (it3.hasNext() && it4.hasNext()) {
                    List<Object> sublist = new ArrayList<>();
                    String p = (String) it3.next();
                    Integer i = it4.next();
                    if (!p.equals("(#)") && !Pattern.matches("^\\{#*}$", p)) {
                        sublist.add(p);
                        sublist.add(i - 1);
                        parentIndexPair.push(sublist);
                    }
                }

                LinkedList<List<Object>> selectedParentPair = new LinkedList<>(parentIndexPair.subList(Math.max(parentIndexPair.size() - params.n_parents, 0), parentIndexPair.size()));
                for (List<Object> sublist : selectedParentPair) {
                    String feat = sublist.get(0).toString() + sublist.get(1).toString() + ">" + currentDict.get("t").toString();
                    params.feat_parents.add(feat);
                }
            }
        }
    }


    public void maintainingSiblings() {
        if (params.current instanceof JSONObject) {
            JSONObject currentDict = (JSONObject) params.current;
            if (currentDict.containsKey("f") && currentDict.getBoolean("f")) {
                if (params.siblings.size() != 0) {
                    LinkedList<List<Object>> selectedSiblings = new LinkedList<>(params.siblings.subList(Math.max(params.siblings.size() - params.n_siblings, 0), params.siblings.size()));
                    for (List<Object> sublist : selectedSiblings) {
                        String feat = sublist.get(1).toString() + ">>" + currentDict.get("t").toString();
                        params.feat_siblings.add(feat);
                    }
                }
                List<Object> sibling = new ArrayList<>();
                sibling.add(params.leaf_idx - 1);
                sibling.add(currentDict.get("t"));
                params.siblings.add(sibling);
            }
        }
    }

    public void maintainingVarSiblings() {
        if (params.is_var) {

            if (!params.parents.getFirst().equals("#.#")) {
                int subleafIndex = params.contentLengthList.getFirst() - params.counter.getFirst() - 1;
                String varContext = params.parents.getFirst() + Integer.toString(subleafIndex);

                if (!params.var_siblings.containsKey(params.globalVarName)) {
                    List<List<Object>> initSublist = new ArrayList<>();
                    params.var_siblings.put(params.globalVarName, initSublist);
                }
                List<List<Object>> correspondingContext = new LinkedList<>(params.var_siblings.get(params.globalVarName));
                List<List<Object>> selectedVarSiblings = new LinkedList<>(correspondingContext.subList(Math.max(correspondingContext.size() - params.n_var_siblings, 0), correspondingContext.size()));
                for (List<Object> selectedVarSibling : selectedVarSiblings) {
                    String feat = selectedVarSibling.get(1) + ">>>" + varContext;
                    params.feat_var_siblings.add(feat);
                }
                List<Object> sublist = new ArrayList<>();
                sublist.add(params.leaf_idx - 1);
                sublist.add(varContext);
                correspondingContext.add(sublist);
                params.var_siblings.put(params.globalVarName, correspondingContext);
            } else {
                params.var_cache.push(params.globalVarName);
                if (!params.var_siblings.containsKey(params.globalVarName)) {
                    List<List<Object>> initSublist = new ArrayList<>();
                    params.var_siblings.put(params.globalVarName, initSublist);
                }
            }
        }
    }

    public void vocabBuilder() {
        for (int i = 0; i < params.all_feat.size(); i++) {
            String currentToken = params.all_feat.get(i);
            List<Integer> initSublist = new ArrayList<>();
            if (!params.vocab.containsKey(currentToken)) {
                initSublist.add(1);
                initSublist.add(i + params.n_reserved);
            } else {
                initSublist.add(params.vocab.get(currentToken).get(0) + 1);
                initSublist.add(params.vocab.get(currentToken).get(1));
            }
            params.vocab.put(currentToken, initSublist);
            params.featList.add(params.vocab.get(currentToken).get(1));
        }
    }


}



