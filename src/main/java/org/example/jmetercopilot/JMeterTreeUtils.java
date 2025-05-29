package org.example.jmetercopilot; // Adjust package name as needed

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.ThreadGroup; // Example type
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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
}
