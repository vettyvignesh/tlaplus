package org.lamport.tla.toolbox.tool.prover.ui.output.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Assert;
import org.lamport.tla.toolbox.tool.prover.job.ProverJob;
import org.lamport.tla.toolbox.tool.prover.ui.preference.ProverPreferencePage;
import org.lamport.tla.toolbox.tool.prover.ui.util.ProverHelper;

/**
 * A class containing information about a proof step that is
 * needed for updating the status of proof steps.
 * 
 * @author Daniel Ricketts
 *
 */
public class StepTuple
{

    /**
     * The parent of this step. Will be null
     * if the step has no parent.
     */
    private StepTuple parent;
    /**
     * The SANY marker for the step. See
     * {@link ProverHelper#SANY_MARKER} for a description
     * of these markers.
     */
    private IMarker sanyMarker;
    /**
     * A list of objects. Each object is an instance of
     * {@link StepTuple} if this step is not a leaf
     * step, or {@link ObligationStatus} if this step
     * is a leaf step.
     */
    private List children;
    /**
     * The ith element of the array gives
     * the value of the (i+1)st color predicate
     * for this step. Color predicates
     * are numbered starting at 1.
     */
    private boolean[] colorPredicateValues;
    /**
     * The job which launched the prover. This step tuple
     * contains the status for that launch of the prover.
     */
    private ProverJob proverJob;

    /**
     * Updates the status of this step. This method
     * computes the value of all color predicates for this step.
     * If the value of one of the color predicates changes,
     * at least one of the color predicates is true for this step,
     * and the new minimum number true color predicate has changed,
     * then this method creates a new sany step marker.
     * 
     * This method calls {@link #updateStatus()}
     * on its parent if the parent is not null and the value
     * of one of the color predicates for this step has changed.
     */
    public void updateStatus()
    {

        // the computation of the value of color predicates
        // depends one whether this step is a leaf step or not
        boolean isLeaf = sanyMarker.getAttribute(ProverHelper.SANY_IS_LEAF_ATR, false);
        ColorPredicate[] colorPredicates = proverJob.getColorPredicates();

        // CurrentMinimum will be set to the value of the current minimum
        // true color predicate for this step, or numberOfPredicates+1
        // if no color predicates are true.
        int oldMinimum = ProverPreferencePage.NUM_STATUS_COLORS + 1;
        for (int i = 0; i < ProverPreferencePage.NUM_STATUS_COLORS; i++)
        {
            if (colorPredicateValues[i])
            {
                oldMinimum = i + 1;
                break;
            }
        }

        // will be set to true if the value of at least one color predicate has changed
        boolean predicateChanged = false;

        /*
         * The following if-else recomputes the value of the color predicates.
         */
        if (isLeaf)
        {
            /*
             * There is one element in the array for each child obligation.
             * The following for loop will set each element to be the obligation
             * state for that child.
             */
            int[] obligationStateNumbers = new int[children.size()];

            for (int i = 0; i < obligationStateNumbers.length; i++)
            {
                obligationStateNumbers[i] = ((ObligationStatus) children.get(i)).getObligationState();
            }

            /*
             * Recompute the value of each of the color predicates from the
             * obligation states.
             */
            for (int i = 0; i < colorPredicateValues.length; i++)
            {
                boolean newPredicateValue = colorPredicates[i].satisfiedByObligations(obligationStateNumbers);
                predicateChanged = predicateChanged || (colorPredicateValues[i] != newPredicateValue);
                colorPredicateValues[i] = newPredicateValue;
            }
        } else
        {

            /*
             * For each color predicate, get the value of that color predicate for each child.
             * The array childPredicateValues stores the value of the color predicate for each
             * child. From the children values of the color predicate we can compute
             * the value of the color predicate for this step.
             */
            boolean[] childPredicateValues = new boolean[children.size()];

            for (int i = 0; i < colorPredicateValues.length; i++)
            {
                // get the value of the color predicate for the children
                int childNum = 0;
                for (Iterator it = children.iterator(); it.hasNext();)
                {
                    childPredicateValues[childNum] = ((StepTuple) it.next()).getColorPredicateValues()[i];
                    childNum++;
                }

                // compute the value of the color predicate for this step
                boolean newPredicateValue = colorPredicates[i].satisfiedBasedOnChildren(childPredicateValues);
                predicateChanged = predicateChanged || (colorPredicateValues[i] != newPredicateValue);
                colorPredicateValues[i] = newPredicateValue;
            }
        }

        /*
         * If at least one color predicate has changed then recompute the
         * minimum true color predicate. If the new minimum true color predicate
         * is less than the old minimum, then create a new step status marker
         * and delete the old one.
         * 
         * If the parent is not null and at least one color predicate has changed,
         * then update the status of the parent.
         */
        if (predicateChanged)
        {
            int newMinimum = ProverPreferencePage.NUM_STATUS_COLORS + 1;
            for (int i = 0; i < colorPredicateValues.length; i++)
            {
                if (colorPredicateValues[i])
                {
                    newMinimum = i + 1;
                    break;
                }
            }

            if (newMinimum < oldMinimum)
            {
                ProverHelper.newStepStatusMarker(sanyMarker, newMinimum);
            }

            if (parent != null)
            {
                parent.updateStatus();
            }

        }

    }

    /**
     * Creates the step tuple with initial status
     * {@link ProverHelper#STEP_UNKNOWN_INT}.
     * @param proverJob the job which launched the prover.
     */
    public StepTuple(ProverJob proverJob)
    {
        this.proverJob = proverJob;
        children = new ArrayList();
        colorPredicateValues = new boolean[ProverPreferencePage.NUM_STATUS_COLORS];
    }

    /**
     * Adds a child to this step. Updates the status.
     * Updating the status calls {@link #updateStatus()}.
     * 
     * This method takes any object as an argument, but
     * the object should be an instance of {@link ObligationStatus}
     * or of {@link StepTuple}. Since these two class
     * have no common method, it does not make sense
     * to have a common interface. However, this method
     * asserts that child is an instance of one of those
     * two classes.
     * 
     * @param child the child. Should be an instance
     * of {@link StepTuple} or {@link ObligationStatus}.
     */
    public void addChild(Object child)
    {
        Assert.isTrue(child instanceof StepTuple || child instanceof ObligationStatus, "An instance of "
                + child.getClass() + " was added as a child to a StepTuple. This is a bug.");
        children.add(child);
        updateStatus();
    }

    /**
     * Sets the SANY marker for the step. See
     * {@link ProverHelper#SANY_MARKER} for a description
     * of these markers.
     * 
     * @param sanyMarker the sanyMarker to set
     */
    public void setSanyMarker(IMarker sanyMarker)
    {
        this.sanyMarker = sanyMarker;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(StepTuple parent)
    {
        this.parent = parent;
    }

    /**
     * Returns the current value of the color predicates for
     * this obligation.
     */
    public boolean[] getColorPredicateValues()
    {
        return colorPredicateValues;
    }

    /**
     * Returns the SANY marker associated with this step.
     * @return
     */
    public IMarker getSanyMarker()
    {
        return sanyMarker;
    }

}