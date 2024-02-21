// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Individual step in a processing flow. */
public abstract class Step {
  public static final String THROWABLE = "throwable";

  private Step next;

  /** Create a step with no next step. */
  protected Step() {
    this(null);
  }

  /**
   * Create a step with the indicated next step.
   *
   * @param next The next step, use null to indicate a terminal step
   */
  protected Step(Step next) {
    this.next = next;
  }

  /**
   * Chain the specified step groups into a single chain of steps.
   *
   * @param stepGroups multiple groups of steps
   * @return the first step of the resultant chain
   */
  public static Step chain(Step... stepGroups) {
    int start = getFirstNonNullIndex(stepGroups);
    if (start >= stepGroups.length) {
      throw new IllegalArgumentException("No non-Null steps specified");
    }

    for (int i = start + 1; i < stepGroups.length; i++) {
      addLink(stepGroups[start], stepGroups[i]);
    }
    return stepGroups[start];
  }

  /**
   * Chain the specified step groups into a single chain of steps.
   *
   * @param stepGroups multiple groups of steps
   * @return the first step of the resultant chain
   */
  public static Step chain(List<Step> stepGroups) {
    return chain(stepGroups.toArray(new Step[0]));
  }

  /**
   * Inserts a step into a chain of steps before the first step whose name is specified. The name is given without
   * its package and/or outer class. If no step with the specified class is found, the step will be inserted at the end.
   * @param stepToInsert the step to insert
   * @param stepClassName the name of the class before which to insert the new step
   */
  public void insertBefore(Step stepToInsert, @Nullable String stepClassName) {
    Step step = this;
    while (step.getNext() != null && !isSpecifiedStep(step.getNext(), stepClassName)) {
      step = step.getNext();
    }
    stepToInsert.next = step.getNext();
    step.next = stepToInsert;
  }

  private boolean isSpecifiedStep(Step step, String stepClassName) {
    return stepClassName != null && hasSpecifiedClassName(step.getClass().getName(), stepClassName);
  }

  private boolean hasSpecifiedClassName(String name, String stepClassName) {
    return name.endsWith("." + stepClassName) || name.endsWith("$" + stepClassName);
  }

  private static int getFirstNonNullIndex(Step[] stepGroups) {
    for (int i = 0; i < stepGroups.length; i++) {
      if (stepGroups[i] != null) {
        return i;
      }
    }

    return stepGroups.length;
  }

  private static void addLink(Step stepGroup1, Step stepGroup2) {
    Step lastStep = lastStepIfNoDuplicate(stepGroup1, stepGroup2);
    if (lastStep != null) {
      // add steps in stepGroup2 to the end of stepGroup1 only if no steps
      // appears in both groups to avoid introducing a loop
      lastStep.next = stepGroup2;
    }
  }

  /**
   * Return last step in stepGroup1, or null if any step appears in both step groups.
   *
   * @param stepGroup1 Step that we want to find the last step for
   * @param stepGroup2 Step to check for duplicates
   *
   * @return last step in stepGroup1, or null if any step appears in both step groups.
   */
  private static Step lastStepIfNoDuplicate(Step stepGroup1, Step stepGroup2) {
    Step s = stepGroup1;
    List<Step> stepGroup2Array = stepToArray(stepGroup2);
    while (s.next != null) {
      if (stepGroup2Array.contains(s.next)) {
        return null;
      }
      s = s.next;
    }
    return s;
  }

  private static List<Step> stepToArray(Step stepGroup) {
    ArrayList<Step> stepsArray = new ArrayList<>();
    Step s = stepGroup;
    while (s != null) {
      stepsArray.add(s);
      s = s.next;
    }
    return stepsArray;
  }

  /**
   * The name of the step. This will default to the class name minus "Step".
   * @return The name of the step
   */
  public String getResourceName() {
    return getBaseName() + getNameSuffix();
  }

  @Nonnull
  private String getBaseName() {
    String name = getClass().getName();
    int idx = name.lastIndexOf('.');
    if (idx >= 0) {
      name = name.substring(idx + 1);
    }
    name = name.endsWith("Step") ? name.substring(0, name.length() - 4) : name;
    return name;
  }

  @Nonnull
  private String getNameSuffix() {
    return Optional.ofNullable(getDetail()).map(detail -> " (" + detail + ")").orElse("");
  }

  protected String getDetail() {
    return null;
  }

  // creates a unique ID that allows matching requests to responses
  public String identityHash() {
    return Integer.toHexString(System.identityHashCode(this));
  }

  @Override
  public String toString() {
    if (next == null) {
      return getResourceName();
    }
    return getResourceName() + "[" + next.toString() + "]";
  }

  /**
   * Invokes step using the packet as input/output context.
   *
   * @param packet Packet
   */
  public abstract Void apply(Packet packet);

  /**
   * Invokes the next step, if set.
   *
   * @param packet Packet to provide when invoking the next step
   */
  protected Void doNext(Packet packet) {
    return doNext(next, packet);
  }

  /**
   * Invokes the indicated next step.
   *
   * @param step The step
   * @param packet Packet to provide when invoking the next step
   */
  protected Void doNext(Step step, Packet packet) {
    if (isCancelled(packet)) {
      return null;
    }
    return Optional.ofNullable(step).map(s -> s.apply(packet)).orElse(null);
  }

  protected boolean isCancelled(Packet packet) {
    return Optional.ofNullable(packet.getFiber()).map(Fiber::isCancelled).orElse(true);
  }

  /**
   * End the fiber processing.
   *
   * @param packet Packet
   */
  protected final Void doEnd(Packet packet) {
    return doNext(null, packet);
  }

  /**
   * Terminate fiber processing with a throwable.
   *
   * @param throwable Throwable
   * @param packet Packet
   * @return Next action that will end processing with a throwable
   */
  protected final Void doTerminate(Throwable throwable, Packet packet) {
    packet.put(THROWABLE, throwable);
    return doEnd(packet);
  }

  /**
   * Retries this step after a delay.
   *
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   */
  @SuppressWarnings("SameParameterValue")
  protected Void doRetry(Packet packet, long delay, TimeUnit unit) {
    return doDelay(this, packet, delay, unit);
  }

  /**
   * Invoke the indicated step after a delay.
   *
   * @param step Step from which to resume
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   */
  protected Void doDelay(Step step, Packet packet, long delay, TimeUnit unit) {
    packet.getFiber().delay(step, packet, delay, unit);
    return null;
  }

  public Step getNext() {
    return next;
  }

  /**
   * Invokes a set of child fibers in parallel and then continues with the indicated step and packet.
   *
   * @param step Step to invoke next when resumed after child fibers complete
   * @param packet Resume packet
   * @param startDetails Pairs of step and packet to use when starting child fibers
   */
  protected Void doForkJoin(
      Step step, Packet packet, Collection<Fiber.StepAndPacket> startDetails) {
    packet.getFiber().forkJoin(step, packet, startDetails);
    return null;
  }
}
