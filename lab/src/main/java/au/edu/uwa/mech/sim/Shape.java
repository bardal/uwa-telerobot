package au.edu.uwa.mech.sim;

/** The kinds of piece on the table, after the hall of fame builds. */
public enum Shape
{
  CUBE("cube"), BAR("bar"), COLUMN("column"), ROOF("roof"), ARCH("arch"), CYLINDER("cylinder");

  private final String noun;

  Shape(String noun) { this.noun = noun; }

  /** The word for one, as the console says it ("added a roof"). */
  public String noun() { return noun; }
}
