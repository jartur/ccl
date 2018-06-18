module Main where

import Lib

f :: (SumE repr) => repr
f = (add (ident 3) (ident 4))

main :: IO ()
main = do 
    print $ toJava f
    print $ toCpp f
