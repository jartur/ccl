module Lib where

someFunc :: IO ()
someFunc = putStrLn "someFunc"

class SumE repr where
    ident :: Int -> repr
    add :: repr -> repr -> repr

instance SumE Int where
    ident x = x
    add x y = x + y

data InitSum = Id Int | Add (InitSum) (InitSum) 
    deriving (Show)

instance SumE (InitSum) where
    ident = Id
    add = Add

newtype Java = Java { unJava :: String } deriving Show
newtype Cpp = Cpp { unCpp :: String } deriving Show

instance SumE Java where
    ident = Java . show
    add x y = Java $ "(" ++ (unJava x) ++ " + " ++ (unJava y) ++ ")"

instance SumE Cpp where
    ident = Cpp . show
    add x y = Cpp $ "(" ++ (unCpp x) ++ " + " ++ (unCpp y) ++ ")"

toFinal :: (SumE repr) => InitSum -> repr
toFinal (Id x) = ident x
toFinal (Add x y) = add (toFinal x) (toFinal y)

eval :: Int -> Int
eval = id

toInit :: InitSum -> InitSum
toInit = id 

toJava :: Java -> Java
toJava = id

toCpp :: Cpp -> Cpp
toCpp = id