package it.unive.lisa.analysis.nonRedundantSet;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticDomain;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.lattices.SetLattice;
import it.unive.lisa.analysis.representation.DomainRepresentation;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.Identifier;

public abstract class NonRedundantPowerset<C extends NonRedundantPowerset<C, T, E, I>,
	T extends SemanticDomain<T, E, I> & Lattice<T>,
	E extends SymbolicExpression,
	I extends Identifier> extends SetLattice<C, T> implements SemanticDomain<C, E, I> {

	T valueDomain;
	
	public NonRedundantPowerset(Set<T> elements, boolean isTop, T valueDomain) {
		super(elements, isTop);
		this.valueDomain = valueDomain;
	}
	
	/**
	 * Yields a new concrete set of elements equivalent to this but that is not redundant. 
	 * An element x of a subset S of the domain of a lattice is redundant iff exists another element y in S such that x &le; y. 
	 * Given a redundant set is always possible to construct an equivalent  set that is not redundant.
	 * This operator is usually called omega reduction (represented as &#937;).
	 * Given a subset S of a domain of a lattice &#937;(S) = S \ {s &ni; S | ( s = bottom ) &vee; ( &exist; s' &ni; S. s &le; s' )}
	 *
	 * 
	 * @return an equivalent element that is not redundant.
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	protected C removeRedundancy() throws SemanticException {
		Set<T> newElementsSet = new HashSet<T>();
		for(T element : this.elements) {
			if(!element.isBottom()) {
				boolean toRemove = false;
				for(T otherElement :  this.elements)
					if(element.lessOrEqual(otherElement) && !otherElement.lessOrEqual(element))
						toRemove = true;
				if(!toRemove)
					newElementsSet.add(element);
			}
		}
		return mk(newElementsSet);
	}


	@Override
	public C top() {
		return mk(new HashSet<T>(), true, this.valueDomain);
	}

	@Override
	public C bottom() {
		return mk(new HashSet<T>(), false, this.valueDomain);
	}
	
	/**
	 * Performs the least upper bound between this non redundant set and the given one. The lub between two non redundant sets is the 
	 * union of the two removed of redundancy.
	 */
	@Override
	public C lubAux(C other)
			throws SemanticException {
		Set<T> lubSet = new HashSet<T>(this.elements);
		lubSet.addAll(other.elements);
		return mk(lubSet).removeRedundancy();
	}

	@Override
	public C glbAux(C other)
			throws SemanticException {
		Set<T> glbSet = new HashSet<T>();
		for(T s1 : this.elements)
			for(T s2 : other.elements)
				glbSet.add(s1.glb(s2));
		return mk(glbSet).removeRedundancy();
	}
	
	/**
	 * An Egli-Milner connector is an upper bound operator for the {@link #lessOrEqualEgliMilner(NonRedundantPowerset) Egli-Milner relation &le;<sub>EM</sub>}. 
	 * An Egli-Milner connector is represented as &boxplus;<sub>EM</sub>.
	 * Given two subsets S<sub>1</sub> and S<sub>2</sub> of a domain of a lattice S<sub>1</sub> &boxplus;<sub>EM</sub> S<sub>2</sub> = S<sub>3</sub>
	 * such that ( S<sub>1</sub> &le;<sub>EM</sub> S<sub>3</sub> ) &wedge; ( S<sub>1</sub> &le;<sub>EM</sub> S<sub>3</sub> ).
	 * The default implementation just performs the lub on the union of the two sets.
	 * 
	 * @param other the other concrete element
	 * 
	 * @return a new set that is &le;<sub>EM</sub> than both this and other
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	protected C EgliMilnerConnector(C other) throws SemanticException{
		Set<T> unionSet = new HashSet<T>(this.elements);
		unionSet.addAll(other.elements);
		T completeLub = valueDomain.bottom();
		if(!unionSet.isEmpty()) {
			for(T element : unionSet) {
				completeLub = completeLub.lub(element);
			}
		}
		Set<T> newSet = new HashSet<T>();
		if(completeLub != null)
			newSet.add(completeLub);
		return mk(newSet);
	}
	
	/**
	 * This method implements the widening-connected hextrapolation heuristic 
	 * proposed in the <a href="https://www.cs.unipr.it/Publications/PDF/Q349.pdf">paper</a> (represented as h<sup>&nabla;</sup>). 
	 * Given two subsets S<sub>1</sub> and S<sub>2</sub> of a domain of a lattice:
	 * <p>
	 * h<sup>&nabla;</sup>( S<sub>1</sub>, S<sub>2</sub>) = 
	 * S<sub>2</sub> &sqcup; &#937;({ s<sub>1</sub> &nabla; s<sub>2</sub> |
	 * 		s<sub>1</sub> &ni; S<sub>1</sub>, s<sub>2</sub> &ni; S<sub>2</sub>, s<sub>1</sub> &lt; s<sub>2</sub>})
	 * </p>
	 * where 
	 * <ul>
	 * <li>&#937; is the {@link #removeRedundancy() omega reduction} operator that removes redundancy from a set,</li>
	 * <li>&nabla; is the widening operator of the underlying lattice,</li>
	 * <li>&lt; is the strict partial order relation of the underlying lattice,</li>
	 * <li>&sqcup; is the {@link #lubAux(NonRedundantPowerset) least upper bound} operator between 
	 * non redundant subsets of the domain of the underlying lattice.</li>
	 * </ul>  
	 * 
	 * @param other the other set to perform the extrapolation with
	 * 
	 * @return the new extrapolated set
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	protected C extrapolationHeuristic(C other) throws SemanticException{
		Set<T> extrapolatedSet = new HashSet<T>();
		for(T s1 : this.elements) {
			for(T s2 : other.elements) {
				if(s1.lessOrEqual(s2) && !s2.lessOrEqual(s1))
					extrapolatedSet.add(s1.widening(s2));
			}
		}
		return mk(extrapolatedSet).removeRedundancy().lub(other);
	}

	
	/**
	 * Perform the wideninig operation between two finite non redundant subsets of the domain of a lattice following 
	 * the Egli-Milner widening implementation shown in this <a href="https://www.cs.unipr.it/Publications/PDF/Q349.pdf">paper</a>.
	 * Given two subset S<sub>1</sub> and S<sub>2</sub> of the domain of a lattice 
	 * widening(S<sub>1</sub>, S<sub>2</sub>) = h<sup>&nabla;</sup>(S<sub>1</sub>, T<sub>2</sub>), where h<sup>&nabla;</sup> is a
	 * {@link #extrapolationHeuristic(NonRedundantPowerset) widenining-connected extrapolation heuristic} and 
	 * T<sub>2</sub> is equal to:
	 * <ul>
	 * <li>S<sub>2</sub> &ensp;&ensp;&ensp;&ensp;&ensp;&ensp;&ensp;&ensp; if S<sub>1</sub> &le;<sub>EM</sub> S<sub>2</sub></li>
	 * <li>S<sub>1</sub> &boxplus;<sub>EM</sub> S<sub>2</sub> &ensp; otherwise</li>
	 * </ul>
	 * where &le;<sub>EM</sub> is the {@link #lessOrEqualEgliMilner(NonRedundantPowerset) Egli-Milner relation} and
	 * &boxplus;<sub>EM</sub> is an {@link #EgliMilnerConnector(NonRedundantPowerset) Egli-Milner connector}.
	 */
	@Override
	public C wideningAux(C other) throws SemanticException {
		if(lessOrEqualEgliMilner(other)) {
			return extrapolationHeuristic(other).removeRedundancy();
		} else {
			return extrapolationHeuristic(EgliMilnerConnector(other)).removeRedundancy();
		}
	}
	
	/**
	 * For two subset S<sub>1</sub> and S<sub>2</sub> of the domain of a lattice S<sub>1</sub> &le;<sub>S</sub> S<sub>2</sub> iff: 
	 * &forall; s<sub>1</sub> &ni; S<sub>1</sub>, &exist; s<sub>2</sub> &ni; S<sub>2</sub> : s<sub>1</sub> &le; s<sub>2</sub>.
	 */
	@Override
	public boolean lessOrEqualAux(C other) throws SemanticException {
		for(T s1 : this.elements) {
			boolean existsGreaterElement = false;
			for(T s2 : other.elements) {
				if(s1.lessOrEqual(s2)) {
					existsGreaterElement = true;
						break;
				}
			}
			if(!existsGreaterElement)
				return false;
		}
		return true;
	}
	
	/**
	 * 
	 * Yields {@code true} if and only if this element is in Egli-Milner relation
	 * with the given one (represented as &le;<sub>EM</sub>). For two subset S<sub>1</sub> and S<sub>2</sub> of the domain of a lattice
	 * S<sub>1</sub> &le;<sub>EM</sub> S<sub>2</sub> iff: 
	 * ( S<sub>1</sub> &le;<sub>S</sub> S<sub>2</sub> ) &wedge; 
	 * ( &forall; s<sub>2</sub> &ni; S<sub>2</sub>, &exist; s<sub>1</sub> &ni; S<sub>1</sub> : s<sub>1</sub> &le; s<sub>2</sub> ). 
	 * Where {@link #lessOrEqualAux(NonRedundantPowerset) &le;<sub>S</sub>} is the less or equal relation between sets.
	 * This operation is not commutative.
	 * 
	 * @param other the other concrete element
	 * 
	 * @return {@code true} if and only if that condition holds
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	public boolean lessOrEqualEgliMilner(C other) throws SemanticException {
		if(lessOrEqual(other)) {
			if(!isBottom()) {
				for(T s2 : other.elements) {
					boolean existsLowerElement = false;
					for(T s1 : this.elements) {
						if(s1.lessOrEqual(s2)) {
							existsLowerElement = true;
							break;
						}
					}
					if(!existsLowerElement)
						return false;
				}
			}
		}
		else
			return false;
		return true;
	}

	@Override
	public C assign(I id, E expression, ProgramPoint pp) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.assign(id, expression, pp));
		}
		return mk(newElements).removeRedundancy();
	}

	@Override
	public C smallStepSemantics(E expression, ProgramPoint pp) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.smallStepSemantics(expression, pp));
		}
		return mk(newElements).removeRedundancy();
	}

	@Override
	public C assume(E expression, ProgramPoint pp) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.assume(expression, pp));
		}
		return mk(newElements).removeRedundancy();
	}

	@Override
	public C forgetIdentifier(Identifier id) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.forgetIdentifier(id));
		}
		return mk(newElements).removeRedundancy();
	}

	@Override
	public C forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.forgetIdentifiersIf(test));
		}
		return mk(newElements).removeRedundancy();
	}


	@Override
	public C pushScope(ScopeToken token) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.pushScope(token));
		}
		return mk(newElements).removeRedundancy();
	}

	@Override
	public C popScope(ScopeToken token) throws SemanticException {
		Set<T> newElements = new HashSet<T>();
		for(T elem : this.elements) {
			newElements.add(elem.popScope(token));
		}
		return mk(newElements).removeRedundancy();
	}

	@Override
	public DomainRepresentation representation() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public C mk(Set<T> set) {
		if(set.isEmpty())
			return mk(set, false, this.valueDomain);
		for(T elem : set) {
			if(elem.isTop())
				return mk(new HashSet<T>(), true, this.valueDomain);
		}
		return mk(set, false, this.valueDomain);
	}
	
	public abstract C mk(Set<T> set, boolean isTop, T valueDomain);

	@Override
	public Satisfiability satisfies(E expression, ProgramPoint pp) throws SemanticException {
		// TODO Auto-generated method stub
		return null;
	}
}
