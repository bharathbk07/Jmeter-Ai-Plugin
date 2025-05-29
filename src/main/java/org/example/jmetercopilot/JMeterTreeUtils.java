package org.example.jmetercopilot; // Adjust package name as needed

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.ThreadGroup; // Example type
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList; // For getChildren
// import org.apache.jorphan.collections.ListedHashTree; // Not strictly needed for the corrected removeElement


public class JMeterTreeUtils {

    /**
     * Gets the GuiPackage instance. This is the entry point to JMeter's GUI model.
     *
     * @return GuiPackage instance, or null if not available (e.g., running in non-GUI mode).
     */
    public static GuiPackage getGuiPackage() {
        return GuiPackage.getInstance();
    }

    /**
     * Gets the root of the current JMeter test plan tree (HashTree).
     *
     * @return The current test plan HashTree, or null if GuiPackage is not available.
     */
    public static HashTree getCurrentTestPlanTree() {
        GuiPackage guiPackage = getGuiPackage();
        if (guiPackage != null && guiPackage.getTreeModel() != null) {
            return guiPackage.getTreeModel().getTestPlan();
        }
        return null;
    }

    /**
     * Finds the first TestElement of a specific class type in the current test plan.
     *
     * @param elementType The Class of the TestElement to find (e.g., ThreadGroup.class).
     * @param <T>         The type of the TestElement.
     * @return The first found TestElement of the specified type, or null if not found or tree is unavailable.
     */
    public static <T extends TestElement> T findFirstElementOfType(Class<T> elementType) {
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null) {
            return null;
        }

        SearchByClass<T> searcher = new SearchByClass<>(elementType);
        testPlan.traverse(searcher);

        Collection<T> elementsFound = searcher.getSearchResults();
        if (!elementsFound.isEmpty()) {
            return elementsFound.iterator().next(); // Return the first one
        }
        return null;
    }

    /**
     * Finds all TestElements of a specific class type in the current test plan.
     *
     * @param elementType The Class of the TestElement to find (e.g., HTTPSamplerProxy.class).
     * @param <T>         The type of the TestElement.
     * @return A List of all found TestElements of the specified type, or an empty list if none found or tree is unavailable.
     */
    public static <T extends TestElement> List<T> findAllElementsOfType(Class<T> elementType) {
        List<T> resultList = new LinkedList<>();
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null) {
            return resultList;
        }

        SearchByClass<T> searcher = new SearchByClass<>(elementType);
        testPlan.traverse(searcher);
        
        resultList.addAll(searcher.getSearchResults());
        return resultList;
    }

    /**
     * Finds a TestElement by its exact "testname" property.
     * Note: Test names may not be unique. This will return the first one found.
     *
     * @param name        The value of the TestElement.NAME property to search for.
     * @param elementType The expected Class of the TestElement (can be TestElement.class for any type).
     * @param <T>         The type of the TestElement.
     * @return The first TestElement found with the given name and type, or null if not found or tree is unavailable.
     */
    public static <T extends TestElement> T findElementByName(String name, Class<T> elementType) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null) {
            return null;
        }

        SearchByName<T> searcher = new SearchByName<>(name, elementType);
        testPlan.traverse(searcher);
        
        return searcher.getFoundElement(); // Custom searcher needed
    }
    
    /**
     * Finds all TestElements that have a "testname" property matching the given name.
     *
     * @param name        The value of the TestElement.NAME property to search for.
     * @param elementType The expected Class of the TestElement (can be TestElement.class for any type).
     * @param <T>         The type of the TestElement.
     * @return A List of TestElements found with the given name and type, or an empty list.
     */
    public static <T extends TestElement> List<T> findAllElementsByName(String name, Class<T> elementType) {
        List<T> resultList = new LinkedList<>();
         if (name == null || name.isEmpty()) {
            return resultList;
        }
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null) {
            return resultList;
        }

        SearchAllByName<T> searcher = new SearchAllByName<>(name, elementType);
        testPlan.traverse(searcher);
        
        return searcher.getFoundElements();
    }


    // Inner helper class for searching by name (first occurrence)
    private static class SearchByName<T extends TestElement> extends SearchByClass<T> {
        private final String elementName;
        private T foundElement = null;

        public SearchByName(String name, Class<T> typeClass) {
            super(typeClass); // This will pre-filter by type
            this.elementName = name;
        }

        @Override
        public void processNode(Object node) {
            if (foundElement != null) return; // Stop searching if already found

            if (node instanceof TestElement) {
                TestElement te = (TestElement) node;
                if (super.getTypeClass().isInstance(te) && elementName.equals(te.getName())) {
                    foundElement = super.getTypeClass().cast(te);
                }
            }
        }
        
        public T getFoundElement() {
            return foundElement;
        }
    }
    
    // Inner helper class for searching all elements by name
    private static class SearchAllByName<T extends TestElement> extends SearchByClass<T> {
        private final String elementName;
        private final List<T> foundElements = new LinkedList<>();

        public SearchAllByName(String name, Class<T> typeClass) {
            super(typeClass);
            this.elementName = name;
        }

        @Override
        public void processNode(Object node) {
            if (node instanceof TestElement) {
                TestElement te = (TestElement) node;
                if (super.getTypeClass().isInstance(te) && elementName.equals(te.getName())) {
                    foundElements.add(super.getTypeClass().cast(te));
                }
            }
        }
        
        public List<T> getFoundElements() {
            return foundElements;
        }
    }


    /**
     * Utility to inform JMeter that the tree structure has changed.
     * This is crucial for the GUI to reflect modifications.
     * This method attempts a general GUI update.
     */
    public static void notifyTreeStructureChanged() {
        GuiPackage guiPackage = getGuiPackage();
        if (guiPackage != null) {
            // Notifying the tree model directly about changes is often more precise,
            // but requires knowing the parent node of the change.
            // For a general refresh:
            guiPackage.getGuiMenu().updateGUI(); 
            guiPackage.getMainFrame().repaint();
            // Consider if more specific updates are needed, e.g.,
            // guiPackage.getTreeModel().nodeStructureChanged(parentNode);
        }
    }
    
    /**
     * Adds a TestElement to a specific target node in the tree.
     *
     * @param parentTree The HashTree of the parent node where the new element should be added.
     * @param newElement The TestElement to add.
     */
    public static HashTree addElementToTree(HashTree parentTree, TestElement newElement) {
        if (parentTree == null || newElement == null) return null;
        return parentTree.add(newElement);
    }

    /**
     * Adds a TestElement as a child of a specified parent TestElement.
     *
     * @param parentElement The TestElement to which the newElement will be added as a child.
     * @param newElement    The TestElement to add.
     * @return The HashTree of the newly added element, or null if parent not found or other error.
     */
    public static HashTree addElementAsChild(TestElement parentElement, TestElement newElement) {
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null || parentElement == null || newElement == null) {
            return null;
        }
        HashTree parentNodeTree = findElementNodeInTree(testPlan, parentElement);
        if (parentNodeTree != null) {
            return parentNodeTree.add(newElement);
        }
        return null;
    }
    
    /**
     * Finds the HashTree node corresponding to a given TestElement instance within a larger tree.
     *
     * @param startingTree The HashTree to search within (e.g., the whole test plan).
     * @param elementToFind The TestElement instance to locate.
     * @return The HashTree node for the elementToFind, or null if not found.
     */
    public static HashTree findElementNodeInTree(HashTree startingTree, TestElement elementToFind) {
        if (startingTree == null || elementToFind == null) return null;

        SearchForElementNode searcher = new SearchForElementNode(elementToFind);
        startingTree.traverse(searcher);
        return searcher.getFoundHashTree();
    }

    // Inner helper class for finding the HashTree of a specific TestElement instance
    private static class SearchForElementNode implements HashTree.Visitor {
        private final TestElement targetElement;
        private HashTree foundHashTree = null;

        public SearchForElementNode(TestElement targetElement) {
            this.targetElement = targetElement;
        }

        @Override
        public void addNode(Object node, HashTree subTree) {
            if (foundHashTree != null) return; // Already found

            if (node == targetElement) { // Check for object instance equality
                foundHashTree = subTree;
            }
        }

        @Override
        public void subtractNode() { /* Not used for this search */ }

        @Override
        public void processPath() { /* Not used for this search */ }
        
        public HashTree getFoundHashTree() {
            return foundHashTree;
        }
    }

    /**
     * Gets all direct child TestElements of a given parent TestElement.
     *
     * @param parentElement The parent TestElement.
     * @return A List of direct child TestElements, or an empty list if none or parent not found.
     */
    public static List<TestElement> getDirectChildren(TestElement parentElement) {
        List<TestElement> children = new ArrayList<>();
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null || parentElement == null) {
            return children;
        }

        HashTree parentNodeSubTree = findElementNodeInTree(testPlan, parentElement);
        if (parentNodeSubTree != null) {
            for (Object key : parentNodeSubTree.keySet()) {
                if (key instanceof TestElement) {
                    children.add((TestElement) key);
                }
            }
        }
        return children;
    }

    /**
     * Removes a specific TestElement from the test plan tree.
     * This method finds the element and its parent to remove it from the parent's HashTree.
     *
     * @param elementToRemove The TestElement to remove.
     * @return true if the element was successfully removed, false otherwise.
     */
    public static boolean removeElementCorrected(TestElement elementToRemove) {
        if (elementToRemove == null) return false;
        HashTree testPlan = getCurrentTestPlanTree();
        if (testPlan == null) return false;
        
        return removeElementRecursive(testPlan, elementToRemove);
    }

    private static boolean removeElementRecursive(HashTree tree, TestElement elementToRemove) {
        // Check if current tree directly contains the element as a key
        // (meaning 'tree' is the parent's own tree, and 'elementToRemove' is a direct child object)
        if (tree.containsKey(elementToRemove)) {
            tree.remove(elementToRemove); // This removes the element and its entire sub-tree
            notifyTreeStructureChanged();
            return true;
        }
        // Otherwise, iterate through the children of the current 'tree'.
        // Each 'key' is a TestElement, and 'tree.get(key)' is the sub-HashTree of that child.
        for (Object key : tree.keySet()) {
            HashTree subTree = tree.get(key); // This is the tree belonging to the child 'key'
            if (removeElementRecursive(subTree, elementToRemove)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the common parent HashTree for a list of TestElements.
     * All elements in the list must be direct children of this common parent.
     *
     * @param startingTree The HashTree to search within (e.g., the whole test plan).
     * @param elements     A list of TestElements that should share a common parent.
     * @return The common parent HashTree, or null if no common parent is found
     *         where all elements are direct children, or if elements list is empty.
     */
    public static HashTree findCommonParentTreeForElements(HashTree startingTree, List<TestElement> elements) {
        if (elements == null || elements.isEmpty() || startingTree == null) {
            return null;
        }

        // Find the parent tree of the first element.
        HashTree firstElementParentTree = findParentTreeHoldingElement(startingTree, elements.get(0));
        if (firstElementParentTree == null) {
            return null; // First element's parent couldn't be found.
        }

        // Check if all other elements also belong to this same parent tree.
        for (int i = 1; i < elements.size(); i++) {
            if (!firstElementParentTree.containsKey(elements.get(i))) {
                return null; // Not all elements are direct children of this parent tree.
            }
        }
        return firstElementParentTree;
    }

    /**
     * Finds the parent HashTree that directly contains the given childElement.
     * This method searches recursively.
     *
     * @param currentTree The current HashTree node being searched. This tree itself is a candidate for being the parent.
     * @param childElement The child TestElement to find the parent tree for.
     * @return The HashTree that is the direct parent of childElement, or null if not found.
     */
    private static HashTree findParentTreeHoldingElement(HashTree currentTree, TestElement childElement) {
        if (currentTree == null || childElement == null) {
            return null;
        }
        // Is childElement a direct key in currentTree? If so, currentTree is its parent HashTree.
        // The keys of a HashTree are the TestElements, and the values are their respective sub-HashTrees.
        if (currentTree.containsKey(childElement)) {
            return currentTree;
        }
        // Recursively search in subtrees. For each key (which is a TestElement) in currentTree,
        // get its associated sub-HashTree (tree.get(key)) and search within that.
        for (Object key : currentTree.keySet()) {
            HashTree subTreeOfKey = currentTree.get(key); 
            HashTree result = findParentTreeHoldingElement(subTreeOfKey, childElement);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
