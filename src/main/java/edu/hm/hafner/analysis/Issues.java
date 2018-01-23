package edu.hm.hafner.analysis;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;

import edu.hm.hafner.util.Ensure;
import edu.hm.hafner.util.NoSuchElementException;
import static java.util.stream.Collectors.*;

/**
 * A set of {@link Issue issues}: it contains no duplicate elements, i.e. it models the mathematical <i>set</i>
 * abstraction. Furthermore, this set of issues provides a <i>total ordering</i> on its elements. I.e., the issues in
 * this set are ordered by their index in this set: the first added issue is at position 0, the second added issues is
 * at position 1, and so on. <p> <p> Additionally, this set of issues provides methods to find and filter issues based
 * on different properties. In order to create issues use the provided {@link IssueBuilder builder} class. </p>
 *
 * @param <T>
 *         type of the issues
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.ExcessivePublicCount")
public class Issues<T extends Issue> implements Iterable<T>, Serializable {
    private static final long serialVersionUID = 1L; // release 1.0.0
    private static final String DEFAULT_ID = "unset";

    private final Set<T> elements = new LinkedHashSet<>();
    private final int[] sizeOfPriority = new int[Priority.values().length];
    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    private int sizeOfDuplicates = 0;
    private String id = DEFAULT_ID;

    /**
     * Returns a predicate that checks if the package name of an issue is equal to the specified package name.
     *
     * @param packageName
     *         the package name to match
     *
     * @return the predicate
     */
    public static Predicate<Issue> byPackageName(final String packageName) {
        return issue -> issue.getPackageName().equals(packageName);
    }

    /**
     * Returns a predicate that checks if the file name of an issue is equal to the specified file name.
     *
     * @param fileName
     *         the package name to match
     *
     * @return the predicate
     */
    public static Predicate<Issue> byFileName(final String fileName) {
        return issue -> issue.getFileName().equals(fileName);
    }

    /**
     * Creates a new empty instance of {@link Issues}.
     */
    public Issues() {
        // no elements to add
    }

    /**
     * Creates a new instance of {@link Issues} that will be initialized with the specified collection of {@link Issue}
     * instances.
     *
     * @param issues
     *         the initial set of issues for this instance
     */
    public Issues(final Collection<? extends T> issues) {
        for (T issue : issues) {
            add(issue);
        }
    }

    /**
     * Creates a new instance of {@link Issues} that will be initialized with the specified collection of {@link Issue}
     * instances.
     *
     * @param issues
     *         the initial set of issues for this instance
     */
    public Issues(final Stream<? extends T> issues) {
        issues.forEach(issue -> add(issue));
    }

    /**
     * Appends all of the specified elements to the end of this container, preserving the order of the array elements.
     * Duplicates will be skipped (the number of skipped elements is available using the method {@link
     * #getDuplicatesSize()}.
     *
     * @param issue
     *         the issue to append
     * @param additionalIssues
     *         the additional issue to append
     */
    @SafeVarargs
    public final void add(final T issue, final T... additionalIssues) {
        add(issue);
        for (T additional : additionalIssues) {
            add(additional);
        }
    }

    private void add(final T issue) {
        if (elements.contains(issue)) {
            sizeOfDuplicates++; // elements are marked as duplicate if the fingerprint is different
        }
        else {
            elements.add(issue);
            sizeOfPriority[issue.getPriority().ordinal()]++;
        }
    }

    /**
     * Appends all of the elements in the specified collection to the end of this container, in the order that they are
     * returned by the specified collection's iterator. Duplicates will be skipped (the number of skipped elements is
     * available using the method {@link #getDuplicatesSize()}.
     *
     * @param issues
     *         the issues to append
     */
    public void addAll(final Collection<? extends T> issues) {
        for (T issue : issues) {
            add(issue);
        }
    }

    /**
     * Appends all of the elements in the specified array of issues to the end of this container, in the order that they
     * are returned by the specified collection's iterator. Duplicates will be skipped (the number of skipped elements
     * is available using the method {@link #getDuplicatesSize()}.
     *
     * @param issues
     *         the issues to append
     * @param additionalIssues
     *         the additional issue to append
     */
    @SafeVarargs
    public final void addAll(final Issues<T> issues, final Issues<T>... additionalIssues) {
        copyIssuesAndProperties(issues, this);
        for (Issues<T> other : additionalIssues) {
            copyIssuesAndProperties(other, this);
        }
    }

    /**
     * Removes the issue with the specified ID. Note that the number of reported duplicates is not affected by calling
     * this method.
     *
     * @param issueId
     *         the ID of the issue
     *
     * @return the removed element
     * @throws NoSuchElementException
     *         if there is no such issue found
     */
    public T remove(final UUID issueId) {
        for (T element : elements) {
            if (element.getId().equals(issueId)) {
                elements.remove(element);
                return element;
            }
        }
        throw new NoSuchElementException("No issue found with id %s.", issueId);
    }

    /**
     * Returns the issue with the specified ID.
     *
     * @param issueId
     *         the ID of the issue
     *
     * @return the found issue
     * @throws NoSuchElementException
     *         if there is no such issue found
     */
    public T findById(final UUID issueId) {
        for (T issue : elements) {
            if (issue.getId().equals(issueId)) {
                return issue;
            }
        }
        throw new NoSuchElementException("No issue found with id %s.", issueId);
    }

    /**
     * Finds all issues that match the specified criterion.
     *
     * @param criterion
     *         the filter criterion
     *
     * @return the found issues
     */
    public Set<T> findByProperty(final Predicate<? super T> criterion) {
        return filterElements(criterion).collect(toSet());
    }

    /**
     * Finds all issues that match the specified criterion.
     *
     * @param criterion
     *         the filter criterion
     *
     * @return the found issues
     */
    public Issues<T> filter(final Predicate<? super T> criterion) {
        Issues<T> filtered = copyEmptyInstance();
        filtered.addAll(filterElements(criterion).collect(toList()));
        return filtered;
    }

    private Stream<T> filterElements(final Predicate<? super T> criterion) {
        return elements.stream().filter(criterion);
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return Lists.immutable.withAll(elements).iterator();
    }

    /**
     * Creates a new sequential {@code Stream} of {@link Issue} instances from a {@code Spliterator}.
     *
     * @return a new sequential {@code Stream}
     */
    public Stream<Issue> stream() {
        return StreamSupport.stream(Spliterators.spliterator(iterator(), 0L, Spliterator.NONNULL), false);
    }

    /**
     * Returns the number of issues in this container.
     *
     * @return total number of issues
     */
    public int size() {
        return elements.size();
    }

    /**
     * Returns whether this container is empty.
     *
     * @return {@code true} if this container is empty, {@code false} otherwise
     * @see #isNotEmpty()
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns whether this container is not empty.
     *
     * @return {@code true} if this container is not empty, {@code false} otherwise
     * @see #isEmpty()
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Returns the number of issues in this container.
     *
     * @return total number of issues
     */
    public int getSize() {
        return size();
    }

    /**
     * Returns the number of duplicates. Every issue that has been added to this container, but already is part of this
     * container (based on {@link #equals(Object)}) is counted as a duplicate. Duplicates are not stored in this
     * container.
     *
     * @return total number of duplicates
     */
    public int getDuplicatesSize() {
        return sizeOfDuplicates;
    }

    /**
     * Returns the number of issues of the specified priority.
     *
     * @param priority
     *         the priority of the issues
     *
     * @return total number of issues
     */
    public int getSizeOf(final Priority priority) {
        return sizeOfPriority[priority.ordinal()];
    }

    /**
     * Returns the number of issues of the specified priority.
     *
     * @param priority
     *         the priority of the issues
     *
     * @return total number of issues
     */
    public int sizeOf(final Priority priority) {
        return getSizeOf(priority);
    }

    /**
     * Returns the number of high priority issues in this container.
     *
     * @return total number of high priority issues
     */
    public int getHighPrioritySize() {
        return getSizeOf(Priority.HIGH);
    }

    /**
     * Returns the number of normal priority issues in this container.
     *
     * @return total number of normal priority issues
     */
    public int getNormalPrioritySize() {
        return getSizeOf(Priority.NORMAL);
    }

    /**
     * Returns the number of low priority issues in this container.
     *
     * @return total number of low priority of issues
     */
    public int getLowPrioritySize() {
        return getSizeOf(Priority.LOW);
    }

    /**
     * Returns the issue with the specified index.
     *
     * @param index
     *         the index
     *
     * @return the issue at the specified index
     * @throws IndexOutOfBoundsException
     *         if there is no element for the given index
     */
    public T get(final int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("No such index " + index + " in " + toString());
        }
        Iterator<T> all = elements.iterator();
        for (int i = 0; i < index; i++) {
            all.next(); // skip this element
        }
        return all.next();
    }

    @Override
    public String toString() {
        return String.format("%d issues (ID = %s)", size(), getId());
    }

    /**
     * Returns the affected modules for all issues of this container.
     *
     * @return the affected modules
     */
    public Set<String> getModules() {
        return getProperties(issue -> issue.getModuleName());
    }

    /**
     * Returns the affected packages for all issues of this container.
     *
     * @return the affected packages
     */
    public Set<String> getPackages() {
        return getProperties(issue -> issue.getPackageName());
    }

    /**
     * Returns the affected files for all issues of this container.
     *
     * @return the affected files
     */
    public Set<String> getFiles() {
        return getProperties(issue -> issue.getFileName());
    }

    /**
     * Returns the used categories for all issues of this container.
     *
     * @return the used categories
     */
    public Set<String> getCategories() {
        return getProperties(issue -> issue.getCategory());
    }

    /**
     * Returns the used types for all issues of this container.
     *
     * @return the used types
     */
    public Set<String> getTypes() {
        return getProperties(issue -> issue.getType());
    }

    /**
     * Returns the names of the tools that did report the issues of this container.
     *
     * @return the tools
     */
    public Set<String> getToolNames() {
        return getProperties(issue -> issue.getOrigin());
    }

    /**
     * Returns the different values for a given property for all issues of this container.
     *
     * @param propertiesMapper
     *         the properties mapper that selects the property
     *
     * @return the set of different values
     * @see #getFiles()
     */
    public Set<String> getProperties(final Function<? super T, String> propertiesMapper) {
        return elements.stream().map(propertiesMapper).collect(toSet());
    }

    /**
     * Returns the number of occurrences for every existing value of a given property for all issues of this container.
     *
     * @param propertiesMapper
     *         the properties mapper that selects the property to evaluate
     *
     * @return a mapping of: property value -> number of issues for that value
     * @see #getProperties(Function)
     */
    public Map<String, Integer> getPropertyCount(final Function<? super T, String> propertiesMapper) {
        return elements.stream().collect(groupingBy(propertiesMapper, reducing(0, issue -> 1, Integer::sum)));
    }

    /**
     * Returns the number of occurrences for every existing value of a given property for all issues of this container.
     *
     * @param propertiesMapper
     *         the properties mapper that selects the property to evaluate
     *
     * @return a mapping of: property value -> number of issues for that value
     * @see #getProperties(Function)
     */
    public Map<String, Issues<T>> groupByProperty(final Function<? super T, String> propertiesMapper) {
        Map<String, List<T>> issues = elements.stream().collect(groupingBy(propertiesMapper));

        return issues.entrySet().stream()
                .collect(toMap(e -> e.getKey(), e -> new Issues<>(e.getValue())));
    }

    /**
     * Returns a shallow copy of this issue container.
     *
     * @return a new issue container that contains the same elements in the same order
     */
    public Issues<T> copy() {
        Issues<T> copied = new Issues<>();
        copyIssuesAndProperties(this, copied);
        return copied;
    }

    private void copyIssuesAndProperties(final Issues<T> source, final Issues<T> destination) {
        if (!destination.hasId()) {
            destination.id = source.id;
        }

        destination.addAll(source.elements);
        copyProperties(source, destination);
    }

    private void copyProperties(final Issues<T> source, final Issues<T> destination) {
        destination.sizeOfDuplicates += source.sizeOfDuplicates;
        destination.infoMessages.addAll(source.infoMessages);
        destination.errorMessages.addAll(source.errorMessages);
    }

    /**
     * Returns a new empty issue container with the same properties as this container. The new
     * issue container is empty and does not contain issues.
     *
     * @return a new issue container that contains the same properties but no issues
     */
    public Issues<T> copyEmptyInstance() {
        Issues<T> empty = new Issues<>();
        empty.setId(id);
        copyProperties(this, empty);
        return empty;
    }

    /**
     * Sets the ID of this set of issues.
     *
     * @param id
     *         ID of this set of issues
     */
    public void setId(final String id) {
        Ensure.that(id).isNotNull();

        this.id = id;
    }

    /**
     * Returns the ID of this set of issues.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns whether this set of issues has an associated ID.
     *
     * @return {@code true} if this set has an ID; {@code false} otherwise
     */
    public boolean hasId() {
        return !DEFAULT_ID.equals(getId());
    }

    /**
     * Logs the specified information message. Use this method to log any useful information when composing this set of issues.
     *
     * @param format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     * @param args
     *         Arguments referenced by the format specifiers in the format string.  If there are more arguments than
     *         format specifiers, the extra arguments are ignored.  The number of arguments is variable and may be
     *         zero.
     *
     * @see #getInfoMessages()
     */
    public void logInfo(final String format, final Object... args) {
        infoMessages.add(String.format(format, args));
    }

    /**
     * Logs the specified error message. Use this method to log any error when composing this set of issues.
     *
     * @param format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     * @param args
     *         Arguments referenced by the format specifiers in the format string.  If there are more arguments than
     *         format specifiers, the extra arguments are ignored.  The number of arguments is variable and may be
     *         zero.
     *
     * @see #getInfoMessages()
     */
    public void logError(final String format, final Object... args) {
        errorMessages.add(String.format(format, args));
    }

    /**
     * Returns the info messages that have been reported since the creation of this set of issues.
     *
     * @return the info messages
     */
    public ImmutableList<String> getInfoMessages() {
        return Lists.immutable.ofAll(infoMessages);
    }

    /**
     * Returns the error messages that have been reported since the creation of this set of issues.
     *
     * @return the error messages
     */
    public ImmutableList<String> getErrorMessages() {
        return Lists.immutable.ofAll(errorMessages);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Issues<?> issues = (Issues<?>) obj;

        if (sizeOfDuplicates != issues.sizeOfDuplicates) {
            return false;
        }
        if (!elements.equals(issues.elements)) {
            return false;
        }
        if (!Arrays.equals(sizeOfPriority, issues.sizeOfPriority)) {
            return false;
        }
        if (!infoMessages.equals(issues.infoMessages)) {
            return false;
        }
        return id.equals(issues.id);
    }

    @Override
    public int hashCode() {
        int result = elements.hashCode();
        result = 31 * result + Arrays.hashCode(sizeOfPriority);
        result = 31 * result + infoMessages.hashCode();
        result = 31 * result + sizeOfDuplicates;
        result = 31 * result + id.hashCode();
        return result;
    }

    /**
     * Builds a combined filter based on several include and exclude filters.
     *
     * @author Raphael Furch
     */
    public static class IssueFilterBuilder {
        private final Collection<Predicate<Issue>> includeFilters = new ArrayList<>();
        private final Collection<Predicate<Issue>> excludeFilters = new ArrayList<>();

        /**
         * Add a new filter for each pattern string. Add filter to include or exclude list.
         *
         * @param pattern
         *         filter pattern.
         * @param propertyToFilter
         *         Function to get a string from Issue for pattern
         */
        private void addNewFilter(final Collection<String> pattern, final Function<Issue, String> propertyToFilter,
                final boolean include) {

            Collection<Predicate<Issue>> filters = new ArrayList<>();
            for (String patter : pattern) {
                filters.add(issueToFilter -> Pattern.compile(patter)
                        .matcher(propertyToFilter.apply(issueToFilter)).matches() == include);
            }

            if (include) {
                includeFilters.addAll(filters);
            }
            else {
                excludeFilters.addAll(filters);
            }
        }

        /**
         * Create a IssueFilter. Combine by default all includes with or and all excludes with and.
         *
         * @return a IssueFilter which has all added filter as filter criteria.
         */
        public Predicate<Issue> build() {
            return includeFilters.stream().reduce(Predicate::or).orElse(issue -> true)
                    .and(excludeFilters.stream().reduce(Predicate::and).orElse(issue -> true));
        }

        //<editor-fold desc="File name">

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeFilenameFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getFileName, true);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeFilenameFilter(final String... pattern) {
            return setIncludeFilenameFilter(Arrays.asList(pattern));
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeFilenameFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getFileName, false);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeFilenameFilter(final String... pattern) {
            return setExcludeFilenameFilter(Arrays.asList(pattern));
        }
        //</editor-fold>

        //<editor-fold desc="Package name">

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludePackageNameFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getPackageName, true);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludePackageNameFilter(final String... pattern) {
            return setIncludePackageNameFilter(Arrays.asList(pattern));
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludePackageNameFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getPackageName, false);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludePackageNameFilter(final String... pattern) {
            return setExcludePackageNameFilter(Arrays.asList(pattern));
        }
        //</editor-fold>

        //<editor-fold desc="Module name">

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeModuleNameFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getModuleName, true);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeModuleNameFilter(final String... pattern) {
            return setIncludeModuleNameFilter(Arrays.asList(pattern));
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeModuleNameFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getModuleName, false);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeModuleNameFilter(final String... pattern) {
            return setExcludeModuleNameFilter(Arrays.asList(pattern));
        }
        //</editor-fold>

        //<editor-fold desc="Category">

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeCategoryFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getCategory, true);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeCategoryFilter(final String... pattern) {
            return setIncludeCategoryFilter(Arrays.asList(pattern));
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeCategoryFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getCategory, false);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeCategoryFilter(final String... pattern) {
            return setExcludeCategoryFilter(Arrays.asList(pattern));
        }
        //</editor-fold>

        //<editor-fold desc="Type">

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeTypeFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getType, true);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setIncludeTypeFilter(final String... pattern) {
            return setIncludeTypeFilter(Arrays.asList(pattern));
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeTypeFilter(final Collection<String> pattern) {
            addNewFilter(pattern, Issue::getType, false);
            return this;
        }

        /**
         * Add a new filter.
         *
         * @param pattern
         *         pattern
         *
         * @return this.
         */
        public IssueFilterBuilder setExcludeTypeFilter(final String... pattern) {
            return setExcludeTypeFilter(Arrays.asList(pattern));
        }
        //</editor-fold>
    }
}
