# Constrained-Data Online Conformance Checking
This prototype implementation is related to the Constrained-Data prefix-alignments based Online Conformance Checking (OCC) approaches. The provided prototype implementation is dependent on the prefix-alignments based Online Conformance package of [[1]](#1) which uses the A<sup>*</sup> algorithm for shortest path search based
prefix-alignment computation. This parent package requires a Petri net process model, its initial marking, and its final marking. Additionally, our approaches require the following additional input parameters:
- The Bounded Cases stateful approach requires a suitable case limit n.
- The bounded Cases and States, which is also a stateful approach, requires a state limit n in addition to a case limit n.
- The ML based Marking Prediction, a stateless approach, requires a feature size f and a case limit n.

## Installation
 - Download the code to your local machine.
 - If required, add the libraries provided as jar files, through Build Path.
 - Run the "ProM with UITopia (CDOCC).launch". Let the code to download all the required ProM packages on the first run.
 - Load your event log and the reference process model in the PROM environment and run one of the following plugin (the first two plugins belong to the stateful aproaches while the last two belong to the stateless approach):
 
    - "02_1 Compute Prefix Alignments Incrementally - With Bound on Traces".
    - "03_1 Compute Prefix Alignments Incrementally - With Bounds on States and Traces".
    - "05_2 Compute Prefix Alignments Incrementally - With Marking Prediction - 10Fold CV".
    - "05_1 Compute Prefix Alignments Incrementally - With Marking Prediction - Train Test Split"

 - Results are locally stored as csv files on the path provided in the parameters.


## References

<a id="1">[1]</a> 
Sebastiaan J van Zelst, Alfredo Bolt, Marwan Hassani, Boudewijn F van Dongen, and Wil MP van der Aalst. (2019).
Online conformance checking: relating event streams to process models using prefix-alignments.
International Journal of Data Science and Analytics 8, 3 (2019), 269–284.
