package zsar.vanillatweaks;

/** Stone Age languages! Gotta love them. */
public class Pointer<T> {
	public T value;
	public Pointer(final T value) { this.value = value; }
	@Override
	public String toString() { return super.toString() + "{ " + this.value + " }"; }
}
