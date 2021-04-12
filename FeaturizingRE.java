import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;
import java.util.regex.Pattern;

public class FeaturizingRE {

    public LinkedList<Object> stack = new LinkedList<>();
    public LinkedList<Object> head = new LinkedList<>();
    public LinkedList<Object> headRem = new LinkedList<>();
    public Object current = new Object();
    public LinkedList<Object> tail;

    public LinkedList<Integer> counter = new LinkedList<>();
    public LinkedList<Integer> contentLengthList = new LinkedList<>();

    public LinkedList<Object> parents = new LinkedList<>();
    public LinkedList<List<Object>> siblings = new LinkedList<>();
    public LinkedList<String> varCache = new LinkedList<>();
    public Map<String, List<List<Object>>> varSiblings = new HashMap<>();

    public ArrayList<String> featSelf = new ArrayList<>();
    public ArrayList<String> featParents = new ArrayList<>();
    public ArrayList<String> featSiblings = new ArrayList<>();
    public ArrayList<String> featVarSiblings = new ArrayList<>();

    public List<String> allFeat = new ArrayList<>();
    public String globalVarName;
    public Map<String, List<Integer>> vocab;
    public ArrayList<Integer> featList;

    public final int nParents = 3;
    public final int nSiblings = 1;
    public final int nVarSiblings = 2;

    public final int nReserved = 10;
    public int leafIdx = 0;

    public boolean currentIsJSON = false;
    public boolean currentIsLeaf = false;
    public boolean isVar = false;

    public List<Object> select = new ArrayList<>();
    public List<List<Object>> nestedSelect = new ArrayList<>();

    public Object iterationAST(JSONArray ast, String returnType) {
        stack = new LinkedList<>(ast);
        while (stack.size() > 0) {
            boolean isArray = false;
            isVar = false;
            currentIsLeaf = false;
            currentIsJSON = false;
            head = new LinkedList<>();

            if (stack.getFirst() instanceof JSONArray) {
                head.addAll((JSONArray) stack.getFirst());
                isArray = true;
            } else {
                head.push(stack.getFirst());
            }

            tail = new LinkedList<>(stack);
            tail.removeFirst();

            current = head.getFirst();

            findVarSiblingsException();

            if (isArray) {
                findParents(head);
            } else {
                findParents(stack);
            }

            findSiblings();
            findVarSiblings();

            headRem = head;
            headRem.removeFirst();
            stack = headRem;
            stack.addAll(tail);
        }

        allFeat.addAll(featSelf);
        allFeat.addAll(featParents);
        allFeat.addAll(featSiblings);
        allFeat.addAll(featVarSiblings);

        return allFeat;
    }


    public void findParents(LinkedList<Object> content) {
        if (parents.size() != 0) {
            while (counter.getFirst() == 0) {
                counter.pop();
                parents.pop();
                contentLengthList.pop();
            }
        }

        if (current instanceof String) {
            currentIsJSON = false;
            currentIsLeaf = false;
            int contentLength = content.size();
            parents.push(current);
            if (counter.size() != 0) {
                int latestIndex = counter.getFirst() - 1;
                counter.pop();
                counter.push(latestIndex);
            }
            counter.push(contentLength);
            contentLengthList.push(contentLength);
        }
        int latestIndex = counter.getFirst() - 1;
        counter.pop();
        counter.push(latestIndex);

        if (currentIsJSON) {
            JSONObject currentDict = (JSONObject) current;
            if (currentIsLeaf) {
                if (isVar) {
                    currentDict.put("t", "#VAR");
                }
                String selfToken = currentDict.getString("t");
                featSelf.add(selfToken);

//                LinkedList<Integer> subLeafIndex = new LinkedList<>();
                LinkedList<List<Object>> parentIndexPair = new LinkedList<>();
//                List<Object> sublist = new ArrayList<>();

                Iterator<Integer> it1 = contentLengthList.iterator();
                Iterator<Integer> it2 = counter.iterator();
                Iterator<Object> it3 = parents.iterator();
                while (it1.hasNext() && it2.hasNext() && it3.hasNext()) {
                    select = new ArrayList<>();
                    int subLeafIndex = it1.next() - it2.next();
                    String p = (String) it3.next();

                    if (!p.equals("(#)") && !Pattern.matches("^\\{#*}$", p)) {
                        select.add(p);
                        select.add(subLeafIndex - 1);
                        parentIndexPair.push(select);
                    }
                }
                List<List<Object>> selectedParentPair = parentIndexPair.subList(Math.max(parentIndexPair.size() - nParents, 0), parentIndexPair.size());
                for (List<Object> pair : selectedParentPair) {
                    featParents.add(pair.get(0).toString() + pair.get(1).toString() + ">" + selfToken);
                }
            }
        }
    }

    public void findSiblings() {
        if (currentIsJSON) {
            JSONObject currentDict = (JSONObject) current;
            if (currentIsLeaf) {
                String selfToken = currentDict.getString("t");
                if (siblings.size() != 0) {
                    List<List<Object>> selectedSiblings = siblings.subList(Math.max(siblings.size() - nSiblings, 0), siblings.size());
                    for (List<Object> sublist : selectedSiblings) {
                        featSiblings.add(sublist.get(1).toString() + ">>" + selfToken);
                    }
                }
                select = new ArrayList<>();
                select.add(leafIdx - 1);
                select.add(selfToken);
                siblings.add(select);
            }
        }
    }

    public void findVarSiblings() {
        if (isVar) {
            boolean exception = false;
            if (!varSiblings.containsKey(globalVarName)) { exception = true;}
            if (!parents.getFirst().equals("#.#")) {
                int subLeafIndex = contentLengthList.getFirst() - counter.getFirst() - 1;
                String varContext = parents.getFirst() + Integer.toString(subLeafIndex);

                if (exception) {
                    nestedSelect.clear();
                    varSiblings.put(globalVarName, nestedSelect);
                }
                List<List<Object>> correspondingContext = varSiblings.get(globalVarName);
                List<List<Object>> selectedVarSiblings = correspondingContext.subList(Math.max(correspondingContext.size() - nVarSiblings, 0), correspondingContext.size());
                for (List<Object> selectedVarSibling : selectedVarSiblings) {
                    featVarSiblings.add(selectedVarSibling.get(1) + ">>>" + varContext);
                }
                select = new ArrayList<>();
                select.add(leafIdx - 1);
                select.add(varContext);
                correspondingContext.add(select);
                varSiblings.put(globalVarName, correspondingContext);
            } else {
                varCache.push(globalVarName);
                if (exception) {
                    nestedSelect.clear();
                    varSiblings.put(globalVarName, nestedSelect);
                }
            }
        }
    }

    public void findVarSiblingsException() {
        if (current instanceof JSONObject) {
            JSONObject currentDict = (JSONObject) current;
            currentIsJSON = true;
            if (currentDict.containsKey("f") && currentDict.getBoolean("f")) {
                currentIsLeaf = true;
                leafIdx += 1;
                globalVarName = currentDict.getString("t");
                if (varCache.size() != 0) {
                    List<List<Object>> correspondingContext = varSiblings.get(varCache.getFirst());
                    List<List<Object>> selectedVarSiblings = correspondingContext.subList(Math.max(correspondingContext.size() - nVarSiblings, 0), correspondingContext.size());
                    for (List<Object> varSibling : selectedVarSiblings) {
                        String feat = varSibling.get(1) + ">>>" + globalVarName;
                        featVarSiblings.add(feat);
                    }
//                    List<Object> sublist = new ArrayList<>();
                    select = new ArrayList<>();
                    select.add(leafIdx - 1);
                    select.add(globalVarName);
                    correspondingContext.add(select);

                    varCache.pop();
                }
                if (currentDict.containsKey("v") && !Character.isUpperCase(globalVarName.charAt(0))) {
                    isVar = true;
                }
            }
        }
    }
}
